package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家客户台账：固定字段 + 客户历史下单记录（FR-033 ~ FR-036）。
 */
@Service
public class CustomerService {

    private final ProductService products;

    public CustomerService(ProductService products) {
        this.products = products;
    }

    public Map<String, Object> list(Connection conn, int page, int pageSize, String keyword)
            throws Exception {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (username LIKE ? OR phone LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM customer WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM customer WHERE " + where + " ORDER BY created_at DESC, rowid DESC "
                        + "LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.customer(row, false);
            item.put("purchase_count", purchaseCount(conn, Jdbc.strOf(row, "id")));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    public Map<String, Object> detail(Connection conn, String customerId) throws Exception {
        Map<String, Object> customer = Jdbc.queryOne(conn,
                "SELECT * FROM customer WHERE id = ?", customerId);
        if (customer == null) {
            throw new NotFoundException("客户不存在");
        }
        Map<String, Object> detail = Serializers.customer(customer, false);
        detail.put("purchase_count", purchaseCount(conn, customerId));

        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM buy_intent WHERE customer_id = ? "
                        + "ORDER BY created_at DESC, rowid DESC LIMIT 200", customerId);
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.intent(row, null);
            item.put("product", products.fullById(conn, Jdbc.strOf(row, "product_id")));
            Map<String, Object> trade = Jdbc.queryOne(conn,
                    "SELECT * FROM trade_record WHERE intent_id = ? "
                            + "ORDER BY created_at DESC, rowid DESC LIMIT 1", Jdbc.strOf(row, "id"));
            item.put("transaction", trade == null ? null : Serializers.trade(trade));
            orders.add(item);
        }
        detail.put("orders", orders);
        return detail;
    }

    private long purchaseCount(Connection conn, String customerId) throws Exception {
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM buy_intent WHERE customer_id = ? AND status = 'SUCCESS'",
                customerId);
        return count == null ? 0L : count;
    }
}
