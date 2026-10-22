package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户购买意向：登录后提交、查看自身意向与历史下单；卖家查看各商品队列。
 * 客户无撤销、无修改意向信息入口（FR-025 / AC-016）。
 */
@Service
public class IntentService {

    private final ProductService products;
    private final QueueService queue;

    public IntentService(ProductService products, QueueService queue) {
        this.products = products;
        this.queue = queue;
    }

    // --------------------------- 客户 ---------------------------

    public Map<String, Object> submit(Connection conn, String customerId, String productId) throws Exception {
        Map<String, Object> product = products.getProduct(conn, productId);
        if (product == null) {
            throw new NotFoundException("商品不存在");
        }
        String status = Jdbc.strOf(product, "status");
        if (Enums.ProductStatus.OFF_SHELF.equals(status)) {
            throw new ConflictException("商品已下架，无法提交购买意向");
        }
        if (Enums.ProductStatus.SOLD_OUT.equals(status)) {
            throw new ConflictException("商品已售罄，暂不能提交意向");
        }
        if (Enums.ProductStatus.FROZEN.equals(status)) {
            throw new ConflictException("商品交易中，暂停接收新意向");
        }
        if (Jdbc.intOf(product, "stock") <= 0) {
            throw new ConflictException("商品库存为 0，暂不能提交意向");
        }
        Map<String, Object> active = Jdbc.queryOne(conn,
                "SELECT id FROM buy_intent WHERE product_id = ? AND customer_id = ? "
                        + "AND status IN ('QUEUED', 'REQUEUED', 'IN_TRANSACTION')",
                productId, customerId);
        if (active != null) {
            throw new ConflictException("您已在该商品的队列中，请勿重复提交");
        }

        String id = Utils.newId();
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "INSERT INTO buy_intent (id, product_id, customer_id, status, queue_position, "
                        + "requeue_count, created_at, updated_at) VALUES (?, ?, ?, ?, 0, 0, ?, ?)",
                id, productId, customerId, Enums.IntentStatus.QUEUED, now, now);
        int position = queue.enqueue(conn, productId, id, now);

        Map<String, Object> intent = Jdbc.queryOne(conn, "SELECT * FROM buy_intent WHERE id = ?", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", Serializers.intent(intent, null));
        result.put("product", products.full(conn, product));
        result.put("queue_position", position);
        result.put("message", "已进入该商品队列，请耐心等待卖家处理");
        return result;
    }

    public Map<String, Object> listMine(Connection conn, String customerId, int page, int pageSize,
                                        String status) throws Exception {
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        StringBuilder where = new StringBuilder("customer_id = ?");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND status = ?");
            params.add(status.trim());
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM buy_intent WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM buy_intent WHERE " + where
                        + " ORDER BY created_at DESC, rowid DESC LIMIT ? OFFSET ?",
                pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.intent(row, null);
            item.put("product", products.fullById(conn, Jdbc.strOf(row, "product_id")));
            item.put("transaction", lastTrade(conn, Jdbc.strOf(row, "id")));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    public Map<String, Object> getMine(Connection conn, String customerId, String intentId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM buy_intent WHERE id = ? AND customer_id = ?", intentId, customerId);
        if (row == null) {
            throw new NotFoundException("意向不存在");
        }
        Map<String, Object> item = Serializers.intent(row, null);
        item.put("product", products.fullById(conn, Jdbc.strOf(row, "product_id")));
        item.put("transaction", lastTrade(conn, intentId));
        return item;
    }

    /** 客户历史下单记录（FR-031）：含商品、状态、时间与交易结果。 */
    public Map<String, Object> orders(Connection conn, String customerId, int page, int pageSize)
            throws Exception {
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM buy_intent WHERE customer_id = ?", customerId);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM buy_intent WHERE customer_id = ? "
                        + "ORDER BY updated_at DESC, rowid DESC LIMIT ? OFFSET ?",
                customerId, pageSize, (page - 1) * pageSize);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.intent(row, null);
            item.put("product", products.fullById(conn, Jdbc.strOf(row, "product_id")));
            item.put("transaction", lastTrade(conn, Jdbc.strOf(row, "id")));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    // --------------------------- 卖家 ---------------------------

    public Map<String, Object> listProductQueue(Connection conn, String productId, int page, int pageSize,
                                                String status) throws Exception {
        products.requireProduct(conn, productId);
        List<Object> params = new ArrayList<>();
        params.add(productId);
        StringBuilder where = new StringBuilder("i.product_id = ?");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND i.status = ?");
            params.add(status.trim());
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM buy_intent i WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT i.*, cu.username, cu.phone, cu.trade_location, cu.created_at AS customer_created_at "
                        + "FROM buy_intent i JOIN customer cu ON cu.id = i.customer_id "
                        + "WHERE " + where + " ORDER BY i.queue_position ASC, i.created_at ASC, i.rowid ASC "
                        + "LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("id", Jdbc.strOf(row, "customer_id"));
            customer.put("username", Jdbc.strOf(row, "username"));
            customer.put("phone", Jdbc.strOf(row, "phone"));
            customer.put("trade_location", Jdbc.strOf(row, "trade_location"));
            customer.put("registered_at", Jdbc.strOf(row, "customer_created_at"));
            items.add(Serializers.intent(row, customer));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    /** 导出意向排队名单 CSV（UTF-8 BOM，便于 Excel 直接打开）。 */
    public String exportCsv(Connection conn, String productId) throws Exception {
        products.requireProduct(conn, productId);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT i.*, cu.username, cu.phone, cu.trade_location "
                        + "FROM buy_intent i JOIN customer cu ON cu.id = i.customer_id "
                        + "WHERE i.product_id = ? ORDER BY i.queue_position ASC, i.created_at ASC",
                productId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("用户名,电话,默认交易地点,状态,排队位次,提交时间,更新时间\n");
        for (Map<String, Object> row : rows) {
            csv.append(csvCell(Jdbc.strOf(row, "username"))).append(',')
                    .append(csvCell(Jdbc.strOf(row, "phone"))).append(',')
                    .append(csvCell(Jdbc.strOf(row, "trade_location"))).append(',')
                    .append(csvCell(Enums.IntentStatus.label(Jdbc.strOf(row, "status")))).append(',')
                    .append(Jdbc.intOf(row, "queue_position")).append(',')
                    .append(csvCell(Jdbc.strOf(row, "created_at"))).append(',')
                    .append(csvCell(Jdbc.strOf(row, "updated_at"))).append('\n');
        }
        return csv.toString();
    }

    private Map<String, Object> lastTrade(Connection conn, String intentId) throws Exception {
        Map<String, Object> trade = Jdbc.queryOne(conn,
                "SELECT * FROM trade_record WHERE intent_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1",
                intentId);
        return trade == null ? null : Serializers.trade(trade);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
