package com.shop.services;

import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;

/**
 * 挑战C 订单：购物车多选下单（待支付）、支付结果回写、库存占用、订单历史与客户取消退款。
 *
 * <p>库存模型：下单即占用（库存占用记录 ACTIVE，可售 = 原始库存 - 活跃占用）；
 * 取消回补（占用置 RELEASED）；交易完成核销（占用置 CONSUMED，由卖家端推进）。
 *
 * <p>订单状态：待支付 → 已支付 → 商家确认 → 备货完成 → 开始发货 → 交易完成；已取消为终态。
 * 已支付订单取消自动原路退款；待支付超过 24 小时由系统自动取消（FR-043）。
 */
@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int PAYMENT_TIMEOUT_HOURS = 24;

    private final ProductService products;
    private final AddressService addresses;
    private final ShipmentService shipments;
    private final Db db;

    public OrderService(ProductService products, AddressService addresses, ShipmentService shipments, Db db) {
        this.products = products;
        this.addresses = addresses;
        this.shipments = shipments;
        this.db = db;
    }

    // --------------------------- 下单 ---------------------------

    /**
     * 购物车多选下单（FR-023 / FR-025 / FR-048）：逐商品校验库存，固化收货地址快照，
     * 订单进入「待支付」。缺货单项提示剔除、不拖累其他商品（TBD-04）。
     */
    public Map<String, Object> checkout(Connection conn, String customerId, List<String> rawProductIds,
                                        String addressId) throws Exception {
        List<String> productIds = PreselectService.distinctIds(rawProductIds);
        if (productIds.isEmpty()) {
            throw new ConflictException("请至少选择一件购物车商品下单");
        }
        if (productIds.size() > PreselectService.MAX_CART_ITEMS) {
            throw new ConflictException("单次下单商品数量不能超过 " + PreselectService.MAX_CART_ITEMS + " 件");
        }
        // FR-048：下单必须选择收货地址；未指定时带出默认地址
        Map<String, Object> address = addresses.resolveForOrder(conn, customerId, addressId);

        List<Map<String, Object>> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int totalCents = 0;
        for (String productId : productIds) {
            Map<String, Object> cart = Jdbc.queryOne(conn,
                    "SELECT * FROM cart_item WHERE customer_id = ? AND product_id = ?", customerId, productId);
            if (cart == null) {
                problems.add(productId + " 不在购物车中");
                continue;
            }
            Map<String, Object> product = Jdbc.queryOne(conn, "SELECT * FROM product WHERE id = ?", productId);
            if (product == null) {
                problems.add(productId + " 商品已不存在");
                continue;
            }
            int quantity = Jdbc.intOf(cart, "quantity");
            String status = Jdbc.strOf(product, "status");
            if (!Enums.ProductStatus.ON_SALE.equals(status)) {
                problems.add(Jdbc.strOf(product, "name") + "（已下架/售罄，请剔除）");
                continue;
            }
            int available = products.availableOf(conn, product);
            if (quantity > available) {
                problems.add(Jdbc.strOf(product, "name") + "（库存不足：需 " + quantity
                        + " 件，可售 " + available + " 件，请剔除或调减数量）");
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
            throw new ConflictException("以下商品无法下单，请剔除后重试：" + String.join("；", problems));
        }

        String now = Utils.nowIso();
        String orderId = Utils.newId();
        String orderNo = nextOrderNo(conn);
        Jdbc.update(conn,
                "INSERT INTO shop_order (id, order_no, customer_id, status, total_cents, receiver_name, "
                        + "receiver_phone, receiver_province, receiver_city, receiver_district, receiver_detail, "
                        + "receiver_postal_code, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderId, orderNo, customerId, Enums.OrderStatus.PENDING_PAYMENT, totalCents,
                Jdbc.strOf(address, "receiver_name"), Jdbc.strOf(address, "receiver_phone"),
                Jdbc.strOf(address, "province"), Jdbc.strOf(address, "city"), Jdbc.strOf(address, "district"),
                Jdbc.strOf(address, "detail"), Jdbc.strOf(address, "postal_code"), now, now);

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
        result.put("order", detailOf(conn, customerId, orderId));
        result.put("message", "下单成功，库存已占用，请在 24 小时内完成支付");
        return result;
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
            items.add(buildOrder(conn, row, false));
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
        result.put("order", detailOf(conn, customerId, orderId));
        return result;
    }

    /**
     * 订单明细（含订单项、地址快照、支付/退款、物流与售后汇总）。
     * 支付与回调服务需要复用，故对外可见。
     */
    public Map<String, Object> detailOf(Connection conn, String customerId, String orderId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM shop_order WHERE id = ? AND customer_id = ?", orderId, customerId);
        if (row == null) {
            throw new NotFoundException("订单不存在");
        }
        return buildOrder(conn, row, false);
    }

    // --------------------------- 客户取消 ---------------------------

    /** 客户取消（FR-028 / FR-044）：开始发货前可取消；已支付订单原路退款，占用库存逐件回补。 */
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
        boolean refunded = cancelInternal(conn, order, "CUSTOMER", "客户主动取消");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", detailOf(conn, customerId, orderId));
        result.put("message", refunded ? "订单已取消，支付金额已原路退回，占用库存已回补"
                : "订单已取消，占用库存已回补");
        return result;
    }

    /** 待支付超时自动取消（FR-043）：超过 24 小时未支付，系统取消并回补库存。 */
    public int expirePendingOrders() {
        String cutoff = Utils.toIso(Utils.now().minusHours(PAYMENT_TIMEOUT_HOURS));
        return db.transaction(conn -> {
            List<Map<String, Object>> rows = Jdbc.query(conn,
                    "SELECT * FROM shop_order WHERE status = ? AND created_at <= ?",
                    Enums.OrderStatus.PENDING_PAYMENT, cutoff);
            for (Map<String, Object> row : rows) {
                cancelInternal(conn, row, "SYSTEM", "超过 24 小时未支付，系统自动取消");
            }
            return rows.size();
        });
    }

    // --------------------------- 卖家端 ---------------------------

    /** 卖家订单列表（FR-031）：按状态筛选、按订单号/客户搜索。 */
    public Map<String, Object> listSeller(Connection conn, int page, int pageSize, String status, String keyword)
            throws Exception {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND o.status = ?");
            params.add(status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (o.order_no LIKE ? OR cu.username LIKE ? OR cu.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM shop_order o JOIN customer cu ON cu.id = o.customer_id WHERE " + where,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT o.* FROM shop_order o JOIN customer cu ON cu.id = o.customer_id WHERE " + where
                        + " ORDER BY o.created_at DESC, o.rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(buildOrder(conn, row, true));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    /** 卖家订单详情（FR-031）：含客户信息、支付/退款、物流与售后。 */
    public Map<String, Object> sellerDetail(Connection conn, String orderId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (row == null) {
            throw new NotFoundException("订单不存在");
        }
        return buildOrder(conn, row, true);
    }

    /** 卖家单向推进（FR-026）：已支付→商家确认→备货完成；备货完成必须发货填物流；开始发货→交易完成。 */
    public Map<String, Object> advance(Connection conn, String sellerId, String orderId) throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        String status = Jdbc.strOf(order, "status");
        String next;
        if (Enums.OrderStatus.PAID.equals(status)) {
            next = Enums.OrderStatus.SELLER_CONFIRMED;
        } else if (Enums.OrderStatus.SELLER_CONFIRMED.equals(status)) {
            next = Enums.OrderStatus.STOCK_READY;
        } else if (Enums.OrderStatus.STOCK_READY.equals(status)) {
            throw new ConflictException("「备货完成」的订单需先填写物流信息发货，不能直接推进");
        } else if (Enums.OrderStatus.SHIPPING.equals(status)) {
            next = Enums.OrderStatus.COMPLETED;
        } else {
            throw new ConflictException("订单当前状态「" + Enums.OrderStatus.label(status) + "」无法推进");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE shop_order SET status = ?, updated_at = ? WHERE id = ?", next, now, orderId);
        if (Enums.OrderStatus.COMPLETED.equals(next)) {
            consumeStock(conn, orderId, now);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", buildOrder(conn,
                Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId), true));
        result.put("message", "订单已推进到「" + Enums.OrderStatus.label(next) + "」");
        return result;
    }

    /** 卖家发货（FR-049）：备货完成 → 开始发货，填写物流服务商与运单号并记录首条物流轨迹。 */
    public Map<String, Object> ship(Connection conn, String sellerId, String orderId, String carrier, String trackingNo)
            throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        if (!Enums.OrderStatus.STOCK_READY.equals(Jdbc.strOf(order, "status"))) {
            throw new ConflictException("仅「备货完成」的订单可以发货");
        }
        Map<String, Object> shipment = shipments.create(conn, orderId, carrier, trackingNo);
        Jdbc.update(conn, "UPDATE shop_order SET status = ?, updated_at = ? WHERE id = ?",
                Enums.OrderStatus.SHIPPING, Utils.nowIso(), orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", buildOrder(conn,
                Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId), true));
        result.put("shipment", shipment);
        result.put("message", "发货成功，订单已进入「开始发货」");
        return result;
    }

    /** 卖家更新物流状态（TBD-09）：单向流转到下一节点。 */
    public Map<String, Object> updateShipmentStatus(Connection conn, String sellerId, String orderId, String status)
            throws Exception {
        Map<String, Object> shipment = shipments.updateStatus(conn, orderId, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipment", shipment);
        result.put("message", "物流状态已更新为「" + Enums.ShipmentStatus.label(status) + "」");
        return result;
    }

    /** 卖家取消（FR-028 / FR-044）：交易完成前可取消；已支付订单原路退款，占用库存回补。 */
    public Map<String, Object> cancelBySeller(Connection conn, String sellerId, String orderId) throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        if (!Enums.OrderStatus.SELLER_CANCELLABLE.contains(Jdbc.strOf(order, "status"))) {
            throw new ConflictException("订单已「" + Enums.OrderStatus.label(Jdbc.strOf(order, "status"))
                    + "」，商家无法取消");
        }
        boolean refunded = cancelInternal(conn, order, "SELLER", "商家取消订单");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", buildOrder(conn,
                Jdbc.queryOne(conn, "SELECT * FROM shop_order WHERE id = ?", orderId), true));
        result.put("message", refunded ? "订单已取消，支付金额已原路退回，占用库存已回补"
                : "订单已取消，占用库存已回补");
        return result;
    }

    /** 交易完成核销占用：活跃占用置已核销，原始库存实际扣减（FR-012 / FR-026）。 */
    private void consumeStock(Connection conn, String orderId, String now) throws Exception {
        List<Map<String, Object>> reservations = Jdbc.query(conn,
                "SELECT product_id, quantity FROM stock_reservation WHERE order_id = ? AND status = ?",
                orderId, Enums.ReservationStatus.ACTIVE);
        Jdbc.update(conn,
                "UPDATE stock_reservation SET status = ?, updated_at = ? WHERE order_id = ? AND status = ?",
                Enums.ReservationStatus.CONSUMED, now, orderId, Enums.ReservationStatus.ACTIVE);
        for (Map<String, Object> reservation : reservations) {
            Jdbc.update(conn, "UPDATE product SET stock = stock - ?, updated_at = ? WHERE id = ?",
                    Jdbc.intOf(reservation, "quantity"), now, Jdbc.strOf(reservation, "product_id"));
            refreshStockStatus(conn, Jdbc.strOf(reservation, "product_id"));
        }
    }

    // --------------------------- 内部工具 ---------------------------

    /** 取消订单：状态置已取消、必要时生成退款、释放占用库存；返回是否发生退款。 */
    private boolean cancelInternal(Connection conn, Map<String, Object> order, String cancelBy, String reason)
            throws Exception {
        String orderId = Jdbc.strOf(order, "id");
        String status = Jdbc.strOf(order, "status");
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "UPDATE shop_order SET status = ?, cancelled_at = ?, cancel_by = ?, cancel_reason = ?, "
                        + "updated_at = ? WHERE id = ?",
                Enums.OrderStatus.CANCELLED, now, cancelBy, reason, now, orderId);

        boolean refunded = false;
        if (Enums.OrderStatus.REFUNDABLE.contains(status)) {
            Map<String, Object> payment = Jdbc.queryOne(conn,
                    "SELECT * FROM payment WHERE order_id = ? AND status = ? ORDER BY rowid DESC LIMIT 1",
                    orderId, Enums.PaymentStatus.SUCCESS);
            if (payment != null) {
                Jdbc.update(conn,
                        "INSERT INTO refund (id, order_id, payment_id, refund_no, amount_cents, channel, status, "
                                + "reason, refunded_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Utils.newId(), orderId, Jdbc.strOf(payment, "id"), nextRefundNo(conn),
                        Jdbc.intOf(payment, "amount_cents"), Jdbc.strOf(payment, "method"),
                        Enums.RefundStatus.SUCCESS, reason, now, now);
                refunded = true;
            }
        }

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
        return refunded;
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

    private Map<String, Object> buildOrder(Connection conn, Map<String, Object> row, boolean forSeller) throws Exception {
        String orderId = Jdbc.strOf(row, "id");
        String status = Jdbc.strOf(row, "status");
        Map<String, Object> item = Serializers.order(row);

        if (forSeller) {
            Map<String, Object> customerRow = Jdbc.queryOne(conn,
                    "SELECT * FROM customer WHERE id = ?", Jdbc.strOf(row, "customer_id"));
            if (customerRow != null) {
                Map<String, Object> customer = new LinkedHashMap<>();
                customer.put("id", Jdbc.strOf(customerRow, "id"));
                customer.put("username", Jdbc.strOf(customerRow, "username"));
                customer.put("phone", Jdbc.strOf(customerRow, "phone"));
                customer.put("trade_location", Jdbc.strOf(customerRow, "trade_location"));
                item.put("customer", customer);
                item.put("customer_name", Jdbc.strOf(customerRow, "username"));
            }
        }

        List<Map<String, Object>> orderItems = items(conn, orderId);
        item.put("items", orderItems);
        item.put("item_count", orderItems.size());

        // 收货地址快照（FR-048）：下单时固化，不随地址簿变更
        Map<String, Object> address = new LinkedHashMap<>();
        String province = Jdbc.strOf(row, "receiver_province");
        String city = Jdbc.strOf(row, "receiver_city");
        String district = Jdbc.strOf(row, "receiver_district");
        address.put("receiver_name", Jdbc.strOf(row, "receiver_name"));
        address.put("receiver_phone", Jdbc.strOf(row, "receiver_phone"));
        address.put("receiver", Jdbc.strOf(row, "receiver_name"));
        address.put("phone", Jdbc.strOf(row, "receiver_phone"));
        address.put("province", province);
        address.put("city", city);
        address.put("district", district);
        address.put("region", province + city + district);
        address.put("detail", Jdbc.strOf(row, "receiver_detail"));
        address.put("postal_code", Jdbc.strOf(row, "receiver_postal_code"));
        item.put("address", address);

        Map<String, Object> payment = Jdbc.queryOne(conn,
                "SELECT * FROM payment WHERE order_id = ? ORDER BY rowid DESC LIMIT 1", orderId);
        Map<String, Object> refund = Jdbc.queryOne(conn,
                "SELECT * FROM refund WHERE order_id = ? ORDER BY rowid DESC LIMIT 1", orderId);
        Map<String, Object> shipment = Jdbc.queryOne(conn,
                "SELECT * FROM shipment WHERE order_id = ?", orderId);

        String paymentStatus;
        String paymentStatusLabel;
        if (refund != null) {
            paymentStatus = "REFUNDED";
            paymentStatusLabel = "已退款";
        } else if (payment != null && Enums.PaymentStatus.SUCCESS.equals(Jdbc.strOf(payment, "status"))) {
            paymentStatus = "PAID";
            paymentStatusLabel = "已支付";
        } else {
            paymentStatus = "UNPAID";
            paymentStatusLabel = "未支付";
        }
        Map<String, Object> paymentItem = payment == null ? null : Serializers.payment(payment);
        if (paymentItem != null && refund != null) {
            paymentItem.put("refund_status", Enums.RefundStatus.label(Jdbc.strOf(refund, "status")));
            paymentItem.put("refund_status_label", Enums.RefundStatus.label(Jdbc.strOf(refund, "status")));
            paymentItem.put("refund_amount", Utils.priceFromCents(Jdbc.integerOrNull(refund, "amount_cents")));
            paymentItem.put("refund_no", Jdbc.strOf(refund, "refund_no"));
            paymentItem.put("refunded_at", Jdbc.strOf(refund, "refunded_at"));
        }
        item.put("payment", paymentItem);
        item.put("payment_status", paymentStatus);
        item.put("payment_status_label", paymentStatusLabel);
        item.put("payment_method", payment == null ? null : Jdbc.strOf(payment, "method"));
        item.put("payment_method_label",
                payment == null ? null : Enums.PaymentMethod.label(Jdbc.strOf(payment, "method")));
        item.put("paid_at", Jdbc.strOf(row, "paid_at"));
        item.put("refund", refund == null ? null : Serializers.refund(refund));
        item.put("shipment", shipment == null ? null : shipments.build(conn, shipment));
        item.put("shipment_status",
                shipment == null ? "NOT_SHIPPED" : Jdbc.strOf(shipment, "status"));
        item.put("shipment_status_label", shipment == null ? "未发货"
                : Enums.ShipmentStatus.label(Jdbc.strOf(shipment, "status")));
        item.put("cancelled_at", Jdbc.strOf(row, "cancelled_at"));
        item.put("cancel_by", Jdbc.strOf(row, "cancel_by"));
        item.put("cancel_reason", Jdbc.strOf(row, "cancel_reason"));
        item.put("can_pay", Enums.OrderStatus.PENDING_PAYMENT.equals(status));
        item.put("can_cancel", Enums.OrderStatus.CUSTOMER_CANCELLABLE.contains(status));
        item.put("can_after_sale", Enums.OrderStatus.COMPLETED.equals(status));
        List<Map<String, Object>> aftersales = afterSales(conn, orderId);
        item.put("aftersales", aftersales);
        item.put("after_sale_count", aftersales.size());
        return item;
    }

    /** 订单级售后汇总（含商品名、图片与退款流水），供订单详情逐件回填。 */
    private List<Map<String, Object>> afterSales(Connection conn, String orderId) throws Exception {
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT a.*, oi.product_id, oi.product_name, o.order_no, "
                        + "(SELECT url FROM product_image WHERE product_id = oi.product_id "
                        + " ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image "
                        + "FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id "
                        + "WHERE a.order_id = ? ORDER BY a.created_at DESC, a.rowid DESC", orderId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = Serializers.afterSale(row);
            item.put("order_no", Jdbc.strOf(row, "order_no"));
            item.put("product_image", Jdbc.strOf(row, "primary_image"));
            List<String> images = new ArrayList<>();
            for (Map<String, Object> image : Jdbc.query(conn,
                    "SELECT url FROM after_sale_image WHERE after_sale_id = ? ORDER BY sort_order ASC",
                    Jdbc.strOf(row, "id"))) {
                images.add(Jdbc.strOf(image, "url"));
            }
            item.put("images", images);
            String refundId = Jdbc.strOf(row, "refund_id");
            if (refundId != null && !refundId.isEmpty()) {
                Map<String, Object> refund = Jdbc.queryOne(conn, "SELECT * FROM refund WHERE id = ?", refundId);
                if (refund != null) {
                    item.put("refund_no", Jdbc.strOf(refund, "refund_no"));
                    item.put("refunded_at", Jdbc.strOf(refund, "refunded_at"));
                }
            }
            list.add(item);
        }
        return list;
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

    /** 退款单号：RF + 日期 + 当天自增序号。 */
    private String nextRefundNo(Connection conn) throws Exception {
        String day = Utils.now().format(ORDER_DAY);
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM refund WHERE refund_no LIKE ?", "RF" + day + "-%");
        int sequence = (count == null ? 0 : count.intValue()) + 1;
        return "RF" + day + "-" + String.format("%04d", sequence);
    }
}