package com.shop.services;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.shop.db.Jdbc;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;

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
            where.append(" AND (username LIKE ? ESCAPE '\\' OR phone LIKE ? ESCAPE '\\')");
            String pattern = Utils.likePattern(keyword.trim());
            params.add(pattern);
            params.add(pattern);
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM customer WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(((long) (page - 1)) * pageSize);
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

        // 挑战B：客户历史 = 订单（shop_order），含已取消；仅下单时间一个时间戳
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM shop_order WHERE customer_id = ? "
                        + "ORDER BY created_at DESC, rowid DESC LIMIT 200", customerId);
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.order(row);
            List<Map<String, Object>> orderItems = Jdbc.query(conn,
                    "SELECT * FROM order_item WHERE order_id = ? ORDER BY rowid ASC",
                    Jdbc.strOf(row, "id"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> orderItem : orderItems) {
                items.add(Serializers.orderItem(orderItem));
            }
            item.put("items", items);
            item.put("item_count", items.size());
            orders.add(item);
        }
        detail.put("orders", orders);
        return detail;
    }

    private long purchaseCount(Connection conn, String customerId) throws Exception {
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM shop_order WHERE customer_id = ? AND status = ?",
                customerId, com.shop.enums.Enums.OrderStatus.COMPLETED);
        return count == null ? 0L : count;
    }
}