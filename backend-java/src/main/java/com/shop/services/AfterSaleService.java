package com.shop.services;

import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.shop.db.Jdbc;
import com.shop.dto.AfterSaleRequest;
import com.shop.dto.AfterSaleResolveRequest;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;
import com.shop.web.Validation;

/**
 * 售后服务（FR-051 / FR-052）：客户对已完成订单的订单项提交售后申请（标题、描述、图片），
 * 并查看处理状态与结果。处理动作归卖家端，本轮不实现。
 */
@Service
public class AfterSaleService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_IMAGES = 9;
    private static final Pattern IMAGE_URL = Pattern.compile("^(/|https?://).+\\.(jpg|jpeg|png)$",
            Pattern.CASE_INSENSITIVE);

    public Map<String, Object> create(Connection conn, String customerId, AfterSaleRequest body) throws Exception {
        if (body == null) {
            throw Validation.error("body", "body", "请求体不能为空");
        }
        String orderItemId = Validation.text(body.orderItemId, "body", "order_item_id", 1, 64, "订单项");
        Map<String, Object> item = Jdbc.queryOne(conn,
                "SELECT oi.id, oi.order_id, o.status AS order_status, o.customer_id AS order_customer_id "
                        + "FROM order_item oi JOIN shop_order o ON o.id = oi.order_id WHERE oi.id = ?", orderItemId);
        if (item == null || !customerId.equals(Jdbc.strOf(item, "order_customer_id"))) {
            throw new NotFoundException("订单项不存在");
        }
        if (!Enums.OrderStatus.COMPLETED.equals(Jdbc.strOf(item, "order_status"))) {
            throw new ConflictException("仅「交易完成」的订单可申请售后");
        }
        String title = Validation.text(body.title, "body", "title", 1, 100, "售后标题");
        String description = Validation.text(body.description, "body", "description", 1, 2000, "问题描述");
        List<String> images = validateImages(body.imageUrls);

        String now = Utils.nowIso();
        String afterSaleId = Utils.newId();
        Jdbc.update(conn,
                "INSERT INTO after_sale (id, after_sale_no, order_id, order_item_id, customer_id, title, "
                        + "description, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                afterSaleId, nextNo(conn), Jdbc.strOf(item, "order_id"), orderItemId, customerId, title,
                description, Enums.AfterSaleStatus.PENDING, now, now);
        int index = 0;
        for (String url : images) {
            Jdbc.update(conn,
                    "INSERT INTO after_sale_image (id, after_sale_id, url, sort_order, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Utils.newId(), afterSaleId, url, index++, now);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("after_sale", load(conn, customerId, afterSaleId));
        result.put("message", "售后申请已提交，卖家将尽快处理");
        return result;
    }

    public Map<String, Object> list(Connection conn, String customerId, String status, int page, int pageSize)
            throws Exception {
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        StringBuilder where = new StringBuilder("a.customer_id = ?");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND a.status = ?");
            params.add(status.trim().toUpperCase());
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM after_sale a WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT a.*, oi.product_id, oi.product_name, o.order_no, "
                        + "(SELECT url FROM product_image WHERE product_id = oi.product_id "
                        + " ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image "
                        + "FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id WHERE " + where
                        + " ORDER BY a.created_at DESC, a.rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(build(conn, row, false));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    public Map<String, Object> detail(Connection conn, String customerId, String afterSaleId) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("after_sale", load(conn, customerId, afterSaleId));
        return result;
    }

    private Map<String, Object> load(Connection conn, String customerId, String afterSaleId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT a.*, oi.product_id, oi.product_name, o.order_no, "
                        + "(SELECT url FROM product_image WHERE product_id = oi.product_id "
                        + " ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image "
                        + "FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id "
                        + "WHERE a.id = ? AND a.customer_id = ?", afterSaleId, customerId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        return build(conn, row, false);
    }

    /** 卖家售后列表（含买家信息）。 */
    public Map<String, Object> listForSeller(Connection conn, String status, String keyword, int page, int pageSize)
            throws Exception {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND a.status = ?");
            params.add(status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (o.order_no LIKE ? OR cu.username LIKE ? OR cu.phone LIKE ? OR a.after_sale_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id "
                        + "JOIN customer cu ON cu.id = a.customer_id WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT a.*, oi.product_id, oi.product_name, o.order_no, "
                        + "(SELECT url FROM product_image WHERE product_id = oi.product_id "
                        + " ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image, "
                        + "cu.username, cu.phone, cu.trade_location FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id "
                        + "JOIN customer cu ON cu.id = a.customer_id WHERE " + where
                        + " ORDER BY a.created_at DESC, a.rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(build(conn, row, true));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    /** 卖家售后详情（含买家信息）。 */
    public Map<String, Object> sellerDetail(Connection conn, String afterSaleId) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("after_sale", loadForSeller(conn, afterSaleId));
        return result;
    }

    /** 卖家受理售后：待处理 → 协商中。 */
    public Map<String, Object> accept(Connection conn, String afterSaleId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn, "SELECT * FROM after_sale WHERE id = ?", afterSaleId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        if (!Enums.AfterSaleStatus.PENDING.equals(Jdbc.strOf(row, "status"))) {
            throw new ConflictException("仅「待处理」的售后可受理进入协商");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE after_sale SET status = ?, updated_at = ? WHERE id = ?",
                Enums.AfterSaleStatus.NEGOTIATING, now, afterSaleId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("after_sale", loadForSeller(conn, afterSaleId));
        result.put("message", "售后申请已受理，进入协商处理");
        return result;
    }

    /** 卖家拒绝售后：待处理 → 已拒绝。 */
    public Map<String, Object> reject(Connection conn, String afterSaleId, String reason) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn, "SELECT * FROM after_sale WHERE id = ?", afterSaleId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        if (!Enums.AfterSaleStatus.PENDING.equals(Jdbc.strOf(row, "status"))) {
            throw new ConflictException("仅「待处理」的售后可直接拒绝");
        }
        String note = Validation.text(reason, "body", "reason", 1, 500, "拒绝原因");
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "UPDATE after_sale SET status = ?, result_note = ?, handled_at = ?, updated_at = ? WHERE id = ?",
                Enums.AfterSaleStatus.REJECTED, note, now, now, afterSaleId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("after_sale", loadForSeller(conn, afterSaleId));
        result.put("message", "售后申请已拒绝");
        return result;
    }

    /** 卖家记录处理结果：协商中 → 已解决；退款结果原路退款，退货结果回补库存。 */
    public Map<String, Object> resolve(Connection conn, String afterSaleId, AfterSaleResolveRequest body)
            throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn, "SELECT * FROM after_sale WHERE id = ?", afterSaleId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        if (!Enums.AfterSaleStatus.NEGOTIATING.equals(Jdbc.strOf(row, "status"))) {
            throw new ConflictException("仅「协商中」的售后可记录处理结果");
        }
        if (body == null) {
            throw Validation.error("body", "body", "请求体不能为空");
        }
        String result = body.result == null ? "" : body.result.trim().toUpperCase();
        if (!Enums.AfterSaleResult.ALL.contains(result)) {
            throw Validation.error("body", "result", "处理结果需为 RETURN / REFUND / EXCHANGE / OTHER");
        }
        String note = Validation.text(body.note, "body", "note", 1, 500, "处理说明");
        String now = Utils.nowIso();
        String orderId = Jdbc.strOf(row, "order_id");
        String orderItemId = Jdbc.strOf(row, "order_item_id");

        if (Enums.AfterSaleResult.REFUND.equals(result)) {
            int refundCents = Validation.priceCents(body.refundAmount, "body", "refund_amount");
            Map<String, Object> payment = Jdbc.queryOne(conn,
                    "SELECT * FROM payment WHERE order_id = ? AND status = ? ORDER BY rowid DESC LIMIT 1",
                    orderId, Enums.PaymentStatus.SUCCESS);
            if (payment == null) {
                throw new ConflictException("该订单没有可退款的支付记录");
            }
            if (refundCents > Jdbc.intOf(payment, "amount_cents")) {
                throw new ConflictException("退款金额不能超过订单实付金额");
            }
            String refundId = Utils.newId();
            Jdbc.update(conn,
                    "INSERT INTO refund (id, order_id, payment_id, refund_no, amount_cents, channel, status, "
                            + "reason, refunded_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    refundId, orderId, Jdbc.strOf(payment, "id"), nextRefundNo(conn), refundCents,
                    Jdbc.strOf(payment, "method"), Enums.RefundStatus.SUCCESS, "售后处理：" + note, now, now);
            Jdbc.update(conn,
                    "UPDATE after_sale SET status = ?, result = ?, result_note = ?, refund_id = ?, "
                            + "refund_amount_cents = ?, handled_at = ?, updated_at = ? WHERE id = ?",
                    Enums.AfterSaleStatus.RESOLVED, result, note, refundId, refundCents, now, now, afterSaleId);
        } else if (Enums.AfterSaleResult.RETURN.equals(result)) {
            Map<String, Object> item = Jdbc.queryOne(conn,
                    "SELECT product_id, quantity FROM order_item WHERE id = ?", orderItemId);
            if (item != null) {
                int quantity = Jdbc.intOf(item, "quantity");
                Jdbc.update(conn, "UPDATE product SET stock = stock + ?, updated_at = ? WHERE id = ?",
                        quantity, now, Jdbc.strOf(item, "product_id"));
                Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ? AND status = ?",
                        Enums.ProductStatus.ON_SALE, now, Jdbc.strOf(item, "product_id"),
                        Enums.ProductStatus.SOLD_OUT);
            }
            Jdbc.update(conn,
                    "UPDATE after_sale SET status = ?, result = ?, result_note = ?, handled_at = ?, "
                            + "updated_at = ? WHERE id = ?",
                    Enums.AfterSaleStatus.RESOLVED, result, note, now, now, afterSaleId);
        } else {
            Jdbc.update(conn,
                    "UPDATE after_sale SET status = ?, result = ?, result_note = ?, handled_at = ?, "
                            + "updated_at = ? WHERE id = ?",
                    Enums.AfterSaleStatus.RESOLVED, result, note, now, now, afterSaleId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("after_sale", loadForSeller(conn, afterSaleId));
        response.put("message", "售后处理结果已记录");
        return response;
    }

    private Map<String, Object> loadForSeller(Connection conn, String afterSaleId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT a.*, oi.product_id, oi.product_name, o.order_no, "
                        + "(SELECT url FROM product_image WHERE product_id = oi.product_id "
                        + " ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image, "
                        + "cu.username, cu.phone, cu.trade_location FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "JOIN shop_order o ON o.id = a.order_id "
                        + "JOIN customer cu ON cu.id = a.customer_id "
                        + "WHERE a.id = ?", afterSaleId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        return build(conn, row, true);
    }

    private Map<String, Object> build(Connection conn, Map<String, Object> row, boolean forSeller) throws Exception {
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

        if (forSeller) {
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("id", Jdbc.strOf(row, "customer_id"));
            customer.put("username", Jdbc.strOf(row, "username"));
            customer.put("phone", Jdbc.strOf(row, "phone"));
            customer.put("trade_location", Jdbc.strOf(row, "trade_location"));
            item.put("customer", customer);
        }
        return item;
    }

    private List<String> validateImages(List<String> raw) {
        List<String> images = new ArrayList<>();
        if (raw == null) {
            return images;
        }
        if (raw.size() > MAX_IMAGES) {
            throw Validation.error("body", "image_urls", "最多上传 " + MAX_IMAGES + " 张图片");
        }
        for (String url : raw) {
            String value = url == null ? "" : url.trim();
            if (!IMAGE_URL.matcher(value).matches() || value.length() > 500) {
                throw Validation.error("body", "image_urls", "图片地址需为 jpg / jpeg / png 格式的上传文件");
            }
            images.add(value);
        }
        return images;
    }

    /** 售后单号：AS + 日期 + 当天自增序号。 */
    private String nextNo(Connection conn) throws Exception {
        String day = Utils.now().format(DAY);
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM after_sale WHERE after_sale_no LIKE ?", "AS" + day + "-%");
        int sequence = (count == null ? 0 : count.intValue()) + 1;
        return "AS" + day + "-" + String.format("%04d", sequence);
    }

    /** 退款单号：RF + 日期 + 当天自增序号（与订单取消退款共用同一前缀，串行事务下不冲突）。 */
    private String nextRefundNo(Connection conn) throws Exception {
        String day = Utils.now().format(DAY);
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM refund WHERE refund_no LIKE ?", "RF" + day + "-%");
        int sequence = (count == null ? 0 : count.intValue()) + 1;
        return "RF" + day + "-" + String.format("%04d", sequence);
    }
}