package com.shop.services;

import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;

/**
 * 挑战B 订单：购物车多选下单、库存占用、订单历史与客户取消。
 *
 * <p>库存模型：下单即占用（库存占用记录 ACTIVE，可售 = 原始库存 - 活跃占用）；
 * 取消回补（占用置 RELEASED）；交易完成核销（占用置 CONSUMED，由卖家端推进）。
 */
@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductService products;
    private final LogService logs;

    public OrderService(ProductService products, LogService logs) {
        this.products = products;
        this.logs = logs;
    }

    // --------------------------- 下单 ---------------------------

    /**
     * 购物车多选下单（FR-023 / FR-025）：逐商品校验库存；
     * 缺货单项提示剔除、不拖累其他商品，客户剔除后再下（TBD-04）。
     */
    public Map<String, Object> checkout(Connection conn, String customerId, List<String> rawProductIds)
            throws Exception {
        List<String> productIds = PreselectService.distinctIds(rawProductIds);
        if (productIds.isEmpty()) {
            throw new ConflictException("请至少选择一件购物车商品下单");
        }
        if (productIds.size() > PreselectService.MAX_CART_ITEMS) {
            throw new ConflictException("单次下单商品数量不能超过 " + PreselectService.MAX_CART_ITEMS + " 件");
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        // TBD-04：缺货逐项提示，details 让前端能把具体商品标红并剔除
        List<Map<String, Object>> shortages = new ArrayList<>();
        int totalCents = 0;
        for (String productId : productIds) {
            Map<String, Object> cart = Jdbc.queryOne(conn,
                    "SELECT * FROM cart_item WHERE customer_id = ? AND product_id = ?", customerId, productId);
            if (cart == null) {
                problems.add(productId + " 不在购物车中");
                shortages.add(shortage(productId, productId, 0, 0, "NOT_IN_CART", "不在购物车中"));
                continue;
            }
            Map<String, Object> product = Jdbc.queryOne(conn, "SELECT * FROM product WHERE id = ?", productId);
            if (product == null) {
                problems.add(productId + " 商品已不存在");
                shortages.add(shortage(productId, productId, Jdbc.intOf(cart, "quantity"), 0,
                        "PRODUCT_REMOVED", "商品已不存在"));
                continue;
            }
            int quantity = Jdbc.intOf(cart, "quantity");
            String status = Jdbc.strOf(product, "status");
            if (!Enums.ProductStatus.ON_SALE.equals(status)) {
                problems.add(Jdbc.strOf(product, "name") + "（已下架/售罄，请剔除）");
                shortages.add(shortage(productId, Jdbc.strOf(product, "name"), quantity,
                        products.availableOf(conn, product), "OFF_SALE", "已下架/售罄，请剔除"));
                continue;
            }
            int available = products.availableOf(conn, product);
            if (quantity > available) {
                problems.add(Jdbc.strOf(product, "name") + "（库存不足：需 " + quantity
                        + " 件，可售 " + available + " 件，请剔除或调减数量）");
                shortages.add(shortage(productId, Jdbc.strOf(product, "name"), quantity, available,
                        "INSUFFICIENT", "库存不足：需 " + quantity + " 件，可售 " + available + " 件"));
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("cart_id", Jdbc.strOf(cart, "id"));
            line.put("product_id", productId);
            line.put("product_name", Jdbc.strOf(product, "name"));
            line.put("price_cents", Jdbc.intOf(product, "price_cents"));
            line.put("quantity", quantity);
            lines.add(line);
            totalCents += Jdbc.intOf(product, "price_cents") * quantity;
        }
        if (!problems.isEmpty()) {
            throw new ConflictException("以下商品无法下单，请剔除后重试：" + String.join("；", problems), shortages);
        }

        String now = Utils.nowIso();
        String orderId = Utils.newId();
        String orderNo = nextOrderNo(conn);
        Jdbc.update(conn,
                "INSERT INTO shop_order (id, order_no, customer_id, status, total_cents, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderId, orderNo, customerId, Enums.OrderStatus.CUSTOMER_PLACED, totalCents, now, now);

        for (Map<String, Object> line : lines) {
            int quantity = Jdbc.intOf(line, "quantity");
            int priceCents = Jdbc.intOf(line, "price_cents");
            String productId = Jdbc.strOf(line, "product_id");
            Jdbc.update(conn,
                    "INSERT INTO order_item (id, order_id, product_id, product_name, price_cents, quantity, "
                            + "subtotal_cents) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Utils.newId(), orderId, productId, Jdbc.strOf(line, "product_name"),
                    priceCents, quantity, priceCents * quantity);
            // 下单占用：记录活跃占用，锁死可售（FR-012）
            Jdbc.update(conn,
                    "INSERT INTO stock_reservation (id, order_id, product_id, quantity, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Utils.newId(), orderId, productId, quantity, Enums.ReservationStatus.ACTIVE, now, now);
            // 下单成功的购物车项自动移除（TBD-06）
            Jdbc.update(conn, "DELETE FROM cart_item WHERE id = ?", Jdbc.strOf(line, "cart_id"));
            refreshStockStatus(conn, productId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderDetail(conn, customerId, orderId));
        result.put("message", "下单成功，所选商品库存已占用");
        return result;
    }

    /**
     * 缺货明细（TBD-04）：随 409 的 error.details 下发，
     * 前端据此把具体条目标红并从本次选择中剔除。
     */
    private static Map<String, Object> shortage(String productId, String name, int quantity,
                                                int available, String reason, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("product_id", productId);
        detail.put("name", name);
        detail.put("quantity", quantity);
        detail.put("available", available);
        detail.put("reason", reason);
        detail.put("message", message);
        return detail;
    }

    // --------------------------- 查询 ---------------------------

    public Map<String, Object> listMine(Connection conn, String customerId, int page, int pageSize, String status)
            throws Exception {
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        StringBuilder where = new StringBuilder("customer_id = ?");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND status = ?");
            params.add(status.trim());
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM shop_order WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM shop_order WHERE " + where
                        + " ORDER BY created_at DESC, rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.order(row);
            List<Map<String, Object>> orderItems = items(conn, Jdbc.strOf(row, "id"));
            item.put("items", orderItems);
            item.put("item_count", orderItems.size());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    public Map<String, Object> detail(Connection conn, String customerId, String orderId) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderDetail(conn, customerId, orderId));
        return result;
    }

    // --------------------------- 客户取消 ---------------------------

    /** 客户取消（FR-028）：开始发货前可取消，占用库存逐件回补（FR-013）。 */
    public Map<String, Object> cancel(Connection conn, String customerId, String orderId) throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn,
                "SELECT * FROM shop_order WHERE id = ? AND customer_id = ?", orderId, customerId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        String status = Jdbc.strOf(order, "status");
        if (!Enums.OrderStatus.CUSTOMER_CANCELLABLE.contains(status)) {
            throw new ConflictException("订单已「" + Enums.OrderStatus.label(status) + "」，客户无法取消");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE shop_order SET status = ?, updated_at = ? WHERE id = ?",
                Enums.OrderStatus.CANCELLED, now, orderId);
        // 回补占用：释放活跃占用，商品可售数量恢复
        List<Map<String, Object>> reservations = Jdbc.query(conn,
                "SELECT product_id FROM stock_reservation WHERE order_id = ? AND status = ?",
                orderId, Enums.ReservationStatus.ACTIVE);
        Jdbc.update(conn,
                "UPDATE stock_reservation SET status = ?, updated_at = ? WHERE order_id = ? AND status = ?",
                Enums.ReservationStatus.RELEASED, now, orderId, Enums.ReservationStatus.ACTIVE);
        for (Map<String, Object> row : reservations) {
            refreshStockStatus(conn, Jdbc.strOf(row, "product_id"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderDetail(conn, customerId, orderId));
        result.put("message", "订单已取消，占用库存已回补");
        return result;
    }

    // --------------------------- 卖家订单管理（FR-026 / FR-029 / FR-031） ---------------------------

    /** 商家订单列表：按状态/订单号/客户名筛选 + 分页（FR-031）。 */
    public Map<String, Object> listSeller(Connection conn, int page, int pageSize,
                                          String status, String keyword) throws Exception {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND o.status = ?");
            params.add(status.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (o.order_no LIKE ? ESCAPE '\\' OR cu.username LIKE ? ESCAPE '\\')");
            String pattern = Utils.likePattern(keyword.trim());
            params.add(pattern);
            params.add(pattern);
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM shop_order o JOIN customer cu ON cu.id = o.customer_id "
                        + "WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT o.*, cu.username, cu.phone, cu.trade_location "
                        + "FROM shop_order o JOIN customer cu ON cu.id = o.customer_id WHERE " + where
                        + " ORDER BY o.created_at DESC, o.rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.order(row);
            item.put("customer", sellerCustomerOf(row));
            List<Map<String, Object>> orderItems = items(conn, Jdbc.strOf(row, "id"));
            item.put("items", orderItems);
            item.put("item_count", orderItems.size());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    /** 商家订单详情：订单 + 订单项 + 客户信息。 */
    public Map<String, Object> sellerDetail(Connection conn, String orderId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT o.*, cu.username, cu.phone, cu.trade_location "
                        + "FROM shop_order o JOIN customer cu ON cu.id = o.customer_id WHERE o.id = ?", orderId);
        if (row == null) {
            throw new NotFoundException("订单不存在");
        }
        Map<String, Object> item = Serializers.order(row);
        List<Map<String, Object>> orderItems = items(conn, orderId);
        item.put("items", orderItems);
        item.put("item_count", orderItems.size());
        item.put("customer", sellerCustomerOf(row));
        return item;
    }

    /** 商家单向推进订单（FR-026）：客户下单→商家确认→备货完成→开始发货→交易完成；到达交易完成时实际扣减库存并核销占用（FR-014）。 */
    public Map<String, Object> advance(Connection conn, String sellerId, String orderId) throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        String status = Jdbc.strOf(order, "status");
        String next = Enums.OrderStatus.next(status);
        if (next == null) {
            throw new ConflictException("订单已「" + Enums.OrderStatus.label(status) + "」，无法继续推进");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE shop_order SET status = ?, updated_at = ? WHERE id = ?",
                next, now, orderId);
        String orderNo = Jdbc.strOf(order, "order_no");

        String message;
        if (Enums.OrderStatus.COMPLETED.equals(next)) {
            // 交易完成：实际扣减 product.stock，并将占用核销为 CONSUMED（FR-014）
            List<Map<String, Object>> reservations = Jdbc.query(conn,
                    "SELECT product_id, quantity FROM stock_reservation WHERE order_id = ? AND status = ?",
                    orderId, Enums.ReservationStatus.ACTIVE);
            for (Map<String, Object> reservation : reservations) {
                String productId = Jdbc.strOf(reservation, "product_id");
                int quantity = Jdbc.intOf(reservation, "quantity");
                Jdbc.update(conn,
                        "UPDATE product SET stock = MAX(0, stock - ?), updated_at = ? WHERE id = ?",
                        quantity, now, productId);
                Jdbc.update(conn,
                        "UPDATE stock_reservation SET status = ?, updated_at = ? "
                                + "WHERE order_id = ? AND product_id = ? AND status = ?",
                        Enums.ReservationStatus.CONSUMED, now, orderId, productId,
                        Enums.ReservationStatus.ACTIVE);
                refreshStockStatus(conn, productId);
            }
            logs.logAction(conn, sellerId, Enums.LogAction.COMPLETE_ORDER,
                    "订单 " + orderNo + " 交易完成，占用库存已核销扣减");
            message = "订单已交易完成，库存已实际扣减";
        } else {
            String action = resolveAction(next);
            logs.logAction(conn, sellerId, action, "订单 " + orderNo + " 推进为「" + Enums.OrderStatus.label(next) + "」");
            message = "订单已推进为「" + Enums.OrderStatus.label(next) + "」";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", sellerDetail(conn, orderId));
        result.put("message", message);
        return result;
    }

    /** 商家取消订单（FR-029）：交易完成前可取消，占用库存逐件回补（FR-013）。 */
    public Map<String, Object> cancelBySeller(Connection conn, String sellerId, String orderId)
            throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        String status = Jdbc.strOf(order, "status");
        if (!Enums.OrderStatus.SELLER_CANCELLABLE.contains(status)) {
            throw new ConflictException("订单已「" + Enums.OrderStatus.label(status) + "」，商家无法取消（交易完成/已取消后不可取消）");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE shop_order SET status = ?, updated_at = ? WHERE id = ?",
                Enums.OrderStatus.CANCELLED, now, orderId);
        // 回补占用：释放活跃占用，商品可售数量恢复
        List<Map<String, Object>> reservations = Jdbc.query(conn,
                "SELECT product_id FROM stock_reservation WHERE order_id = ? AND status = ?",
                orderId, Enums.ReservationStatus.ACTIVE);
        Jdbc.update(conn,
                "UPDATE stock_reservation SET status = ?, updated_at = ? WHERE order_id = ? AND status = ?",
                Enums.ReservationStatus.RELEASED, now, orderId, Enums.ReservationStatus.ACTIVE);
        for (Map<String, Object> reservation : reservations) {
            refreshStockStatus(conn, Jdbc.strOf(reservation, "product_id"));
        }
        logs.logAction(conn, sellerId, Enums.LogAction.CANCEL_ORDER,
                "商家取消订单 " + Jdbc.strOf(order, "order_no") + "，占用库存已回补");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", sellerDetail(conn, orderId));
        result.put("message", "订单已取消，占用库存已回补");
        return result;
    }

    private static String resolveAction(String status) {
        if (Enums.OrderStatus.SELLER_CONFIRMED.equals(status)) {
            return Enums.LogAction.CONFIRM_ORDER;
        }
        if (Enums.OrderStatus.STOCK_READY.equals(status)) {
            return Enums.LogAction.READY_ORDER;
        }
        if (Enums.OrderStatus.SHIPPING.equals(status)) {
            return Enums.LogAction.SHIP_ORDER;
        }
        return Enums.LogAction.CONFIRM_ORDER;
    }

    private static Map<String, Object> sellerCustomerOf(Map<String, Object> row) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", Jdbc.strOf(row, "customer_id"));
        customer.put("username", Jdbc.strOf(row, "username"));
        customer.put("phone", Jdbc.strOf(row, "phone"));
        customer.put("trade_location", Jdbc.strOf(row, "trade_location"));
        return customer;
    }

    /** 可售归零自动售罄下架，回补后可售恢复则重新在售（FR-015 / FR-016）。 */
    private void refreshStockStatus(Connection conn, String productId) throws Exception {
        Map<String, Object> product = Jdbc.queryOne(conn, "SELECT * FROM product WHERE id = ?", productId);
        if (product == null) {
            return;
        }
        String status = Jdbc.strOf(product, "status");
        int available = products.availableOf(conn, product);
        if (available <= 0 && Enums.ProductStatus.ON_SALE.equals(status)) {
            Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                    Enums.ProductStatus.SOLD_OUT, Utils.nowIso(), productId);
        } else if (available > 0 && Enums.ProductStatus.SOLD_OUT.equals(status)) {
            Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                    Enums.ProductStatus.ON_SALE, Utils.nowIso(), productId);
        }
    }

    private Map<String, Object> orderDetail(Connection conn, String customerId, String orderId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM shop_order WHERE id = ? AND customer_id = ?", orderId, customerId);
        if (row == null) {
            throw new NotFoundException("订单不存在");
        }
        Map<String, Object> item = Serializers.order(row);
        List<Map<String, Object>> orderItems = items(conn, orderId);
        item.put("items", orderItems);
        item.put("item_count", orderItems.size());
        return item;
    }

    private List<Map<String, Object>> items(Connection conn, String orderId) throws Exception {
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM order_item WHERE order_id = ? ORDER BY rowid ASC", orderId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Serializers.orderItem(row));
        }
        return items;
    }

    /** 订单号：日期 + 当天自增序号，如 20261107-0001（TBD-10）。 */
    private String nextOrderNo(Connection conn) throws Exception {
        String day = Utils.now().format(ORDER_DAY);
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM shop_order WHERE order_no LIKE ?", day + "-%");
        int sequence = (count == null ? 0 : count.intValue()) + 1;
        return day + "-" + String.format("%04d", sequence);
    }
}