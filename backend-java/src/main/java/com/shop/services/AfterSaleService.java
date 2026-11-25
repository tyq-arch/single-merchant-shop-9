package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.dto.AfterSaleRequest;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;
import com.shop.web.Validation;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
                "SELECT a.*, oi.product_id, oi.product_name FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id WHERE " + where
                        + " ORDER BY a.created_at DESC, a.rowid DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(build(conn, row));
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
                "SELECT a.*, oi.product_id, oi.product_name FROM after_sale a "
                        + "JOIN order_item oi ON oi.id = a.order_item_id "
                        + "WHERE a.id = ? AND a.customer_id = ?", afterSaleId, customerId);
        if (row == null) {
            throw new NotFoundException("售后申请不存在");
        }
        return build(conn, row);
    }

    private Map<String, Object> build(Connection conn, Map<String, Object> row) throws Exception {
        Map<String, Object> item = Serializers.afterSale(row);
        List<String> images = new ArrayList<>();
        for (Map<String, Object> image : Jdbc.query(conn,
                "SELECT url FROM after_sale_image WHERE after_sale_id = ? ORDER BY sort_order ASC",
                Jdbc.strOf(row, "id"))) {
            images.add(Jdbc.strOf(image, "url"));
        }
        item.put("images", images);
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
}
