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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品管理：多商品在售、二级类别、库存驱动上下架、多图、搜索与历史。
 */
@Service
public class ProductService {

    public static final int MAX_IMAGES = 9;

    private static final String SELECT_PRODUCT =
            "SELECT p.*, c.name AS category_name, c.parent_id AS parent_category_id, "
                    + "pc.name AS parent_category_name "
                    + "FROM product p JOIN category c ON c.id = p.category_id "
                    + "LEFT JOIN category pc ON pc.id = c.parent_id ";

    private final CategoryService categories;
    private final LogService logs;

    public ProductService(CategoryService categories, LogService logs) {
        this.categories = categories;
        this.logs = logs;
    }

    // --------------------------- 查询 ---------------------------

    public Map<String, Object> getProduct(Connection conn, String productId) throws Exception {
        return Jdbc.queryOne(conn, SELECT_PRODUCT + "WHERE p.id = ?", productId);
    }

    public List<Map<String, Object>> images(Connection conn, String productId) throws Exception {
        return Jdbc.query(conn,
                "SELECT * FROM product_image WHERE product_id = ? ORDER BY sort_order ASC", productId);
    }

    public Map<String, Object> full(Connection conn, Map<String, Object> row) throws Exception {
        return Serializers.product(row, images(conn, Jdbc.strOf(row, "id")));
    }

    public Map<String, Object> fullById(Connection conn, String productId) throws Exception {
        Map<String, Object> row = getProduct(conn, productId);
        return row == null ? null : full(conn, row);
    }

    /** 公开列表：仅展示在售与交易中商品，支持关键词与类别筛选（FR-010 / FR-019）。 */
    public Map<String, Object> listPublic(Connection conn, int page, int pageSize,
                                          String keyword, String categoryId) throws Exception {
        StringBuilder where = new StringBuilder("p.status IN ('ON_SALE', 'FROZEN')");
        List<Object> params = new ArrayList<>();
        appendFilters(conn, where, params, keyword, categoryId);
        return pageOf(conn, where.toString(), params, page, pageSize);
    }

    /** 卖家商品列表。 */
    public Map<String, Object> listSeller(Connection conn, int page, int pageSize,
                                          String keyword, String status, String categoryId) throws Exception {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND p.status = ?");
            params.add(status.trim());
        }
        appendFilters(conn, where, params, keyword, categoryId);
        return pageOf(conn, where.toString(), params, page, pageSize);
    }

    /** 历史商品：已售罄下架 / 手动下架（FR-012）。 */
    public Map<String, Object> listHistory(Connection conn, int page, int pageSize,
                                           String keyword) throws Exception {
        StringBuilder where = new StringBuilder("p.status IN ('SOLD_OUT', 'OFF_SHELF')");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND p.name LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        Map<String, Object> result = pageOf(conn, where.toString(), params, page, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        for (Map<String, Object> item : items) {
            item.put("transaction_summary", summary(conn, Jdbc.strOf(item, "id")));
        }
        return result;
    }

    public Map<String, Object> historyDetail(Connection conn, String productId) throws Exception {
        Map<String, Object> row = getProduct(conn, productId);
        if (row == null) {
            throw new NotFoundException("商品不存在");
        }
        String status = Jdbc.strOf(row, "status");
        if (!Enums.ProductStatus.HISTORY.contains(status)) {
            throw new ConflictException("该商品尚未下架或售罄，暂不进入历史记录");
        }
        Map<String, Object> detail = full(conn, row);
        detail.put("transaction_summary", summary(conn, productId));

        List<Map<String, Object>> orders = Jdbc.query(conn,
                "SELECT i.*, cu.username, cu.phone, cu.trade_location, cu.created_at AS customer_created_at "
                        + "FROM buy_intent i JOIN customer cu ON cu.id = i.customer_id "
                        + "WHERE i.product_id = ? ORDER BY i.created_at ASC, i.rowid ASC", productId);
        List<Map<String, Object>> buyers = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Map<String, Object> customer = customerOf(order);
            Map<String, Object> intent = Serializers.intent(order, customer);
            intent.put("queue_position", Jdbc.intOf(order, "queue_position"));
            buyers.add(intent);
        }
        detail.put("buyers", buyers);

        List<Map<String, Object>> trades = Jdbc.query(conn,
                "SELECT * FROM trade_record WHERE product_id = ? ORDER BY created_at ASC, rowid ASC", productId);
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Map<String, Object> trade : trades) {
            serialized.add(Serializers.trade(trade));
        }
        detail.put("transactions", serialized);
        return detail;
    }

    // --------------------------- 发布与上下架 ---------------------------

    public Map<String, Object> publish(Connection conn, String sellerId, String name, String description,
                                       int priceCents, int stock, String categoryId,
                                       List<String> imageUrls) throws Exception {
        categories.requireLeaf(conn, categoryId);

        Set<String> uniqueUrls = new LinkedHashSet<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    uniqueUrls.add(url.trim());
                }
            }
        }
        if (uniqueUrls.size() > MAX_IMAGES) {
            throw new ConflictException("商品图片最多 " + MAX_IMAGES + " 张");
        }
        for (String url : uniqueUrls) {
            if (!url.startsWith("/uploads/")) {
                throw new ConflictException("商品图片必须使用本地上传地址");
            }
            String lower = url.toLowerCase();
            if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                throw new ConflictException("商品图片仅支持 JPG / PNG 格式");
            }
        }

        String id = Utils.newId();
        String now = Utils.nowIso();
        String status = stock > 0 ? Enums.ProductStatus.ON_SALE : Enums.ProductStatus.SOLD_OUT;
        Jdbc.update(conn,
                "INSERT INTO product (id, seller_id, category_id, name, description, price_cents, stock, "
                        + "status, published_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sellerId, categoryId, name, description, priceCents, stock, status, now, now, now);

        int order = 1;
        for (String url : uniqueUrls) {
            Jdbc.update(conn,
                    "INSERT INTO product_image (id, product_id, url, sort_order, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Utils.newId(), id, url, order++, now);
        }
        logs.logAction(conn, sellerId, Enums.LogAction.PUBLISH_PRODUCT, "发布商品：" + name);
        return fullById(conn, id);
    }

    /** 调整库存：非负；为 0 自动下架并作废排队意向；由 0 恢复则重新上架（FR-005 / FR-006 / FR-014）。 */
    public Map<String, Object> adjustStock(Connection conn, String sellerId, String productId, int newStock)
            throws Exception {
        Map<String, Object> product = requireProduct(conn, productId);
        String status = Jdbc.strOf(product, "status");
        Map<String, Object> inTx = Jdbc.queryOne(conn,
                "SELECT id FROM buy_intent WHERE product_id = ? AND status = ? LIMIT 1",
                productId, Enums.IntentStatus.IN_TRANSACTION);
        if (inTx != null) {
            throw new ConflictException("商品交易中，暂不能调整库存");
        }

        String now = Utils.nowIso();
        String message;
        Jdbc.update(conn, "UPDATE product SET stock = ?, updated_at = ? WHERE id = ?", newStock, now, productId);
        if (newStock == 0) {
            Jdbc.update(conn,
                    "UPDATE buy_intent SET status = ?, updated_at = ? WHERE product_id = ? AND status IN (?, ?)",
                    Enums.IntentStatus.SOLD_OUT, now, productId,
                    Enums.IntentStatus.QUEUED, Enums.IntentStatus.REQUEUED);
            Jdbc.update(conn, "DELETE FROM queue_item WHERE product_id = ?", productId);
            Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                    Enums.ProductStatus.SOLD_OUT, now, productId);
            message = "库存已调整为 0，商品自动下架，排队意向已售罄作废";
        } else {
            if (Enums.ProductStatus.SOLD_OUT.equals(status) || Enums.ProductStatus.OFF_SHELF.equals(status)) {
                Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                        Enums.ProductStatus.ON_SALE, now, productId);
                message = "库存已调整为 " + newStock + "，商品恢复在售";
            } else {
                message = "库存已调整为 " + newStock;
            }
        }
        logs.logAction(conn, sellerId, Enums.LogAction.ADJUST_STOCK,
                "商品 " + Jdbc.strOf(product, "name") + " 库存调整为 " + newStock + "：" + message);
        return resultWithMessage(conn, productId, message);
    }

    /** 手动下架：排队意向按 TBD-13 默认作废。 */
    public Map<String, Object> offShelf(Connection conn, String sellerId, String productId) throws Exception {
        Map<String, Object> product = requireProduct(conn, productId);
        String status = Jdbc.strOf(product, "status");
        if (Enums.ProductStatus.OFF_SHELF.equals(status)) {
            throw new ConflictException("商品已下架");
        }
        if (hasActiveTransaction(conn, productId)) {
            throw new ConflictException("商品交易中，无法手动下架");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "UPDATE buy_intent SET status = ?, updated_at = ? WHERE product_id = ? AND status IN (?, ?)",
                Enums.IntentStatus.VOIDED, now, productId,
                Enums.IntentStatus.QUEUED, Enums.IntentStatus.REQUEUED);
        Jdbc.update(conn, "DELETE FROM queue_item WHERE product_id = ?", productId);
        Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                Enums.ProductStatus.OFF_SHELF, now, productId);
        logs.logAction(conn, sellerId, Enums.LogAction.OFF_SHELF_PRODUCT,
                "手动下架商品：" + Jdbc.strOf(product, "name") + "，排队意向已作废");
        return resultWithMessage(conn, productId, "商品已手动下架，排队意向已作废");
    }

    /** 重新上架：手动下架商品且库存 > 0。 */
    public Map<String, Object> onShelf(Connection conn, String sellerId, String productId) throws Exception {
        Map<String, Object> product = requireProduct(conn, productId);
        if (!Enums.ProductStatus.OFF_SHELF.equals(Jdbc.strOf(product, "status"))) {
            throw new ConflictException("仅手动下架的商品可以重新上架");
        }
        if (Jdbc.intOf(product, "stock") <= 0) {
            throw new ConflictException("库存为 0，请先补货后再上架");
        }
        Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                Enums.ProductStatus.ON_SALE, Utils.nowIso(), productId);
        logs.logAction(conn, sellerId, Enums.LogAction.ON_SHELF_PRODUCT,
                "重新上架商品：" + Jdbc.strOf(product, "name"));
        return resultWithMessage(conn, productId, "商品已重新上架，恢复接收意向");
    }

    /** 手动冻结（TBD-15 保留）：暂停接收新意向。 */
    public Map<String, Object> freeze(Connection conn, String sellerId, String productId) throws Exception {
        Map<String, Object> product = requireProduct(conn, productId);
        String status = Jdbc.strOf(product, "status");
        if (Enums.ProductStatus.FROZEN.equals(status)) {
            throw new ConflictException("商品已处于冻结状态");
        }
        if (!Enums.ProductStatus.ON_SALE.equals(status)) {
            throw new ConflictException("仅在售商品可以冻结");
        }
        Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                Enums.ProductStatus.FROZEN, Utils.nowIso(), productId);
        logs.logAction(conn, sellerId, Enums.LogAction.FREEZE_PRODUCT,
                "手动冻结商品：" + Jdbc.strOf(product, "name") + "，暂停接收新意向");
        return resultWithMessage(conn, productId, "商品已冻结，不再接收新意向");
    }

    /** 手动解冻：存在进行中交易时禁止。 */
    public Map<String, Object> unfreeze(Connection conn, String sellerId, String productId) throws Exception {
        Map<String, Object> product = requireProduct(conn, productId);
        if (!Enums.ProductStatus.FROZEN.equals(Jdbc.strOf(product, "status"))) {
            throw new ConflictException("仅已冻结的商品可以解冻");
        }
        if (hasActiveTransaction(conn, productId)) {
            throw new ConflictException("存在进行中的交易，不能手动解冻");
        }
        if (Jdbc.intOf(product, "stock") <= 0) {
            throw new ConflictException("库存为 0，请先补货后再解冻");
        }
        Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                Enums.ProductStatus.ON_SALE, Utils.nowIso(), productId);
        logs.logAction(conn, sellerId, Enums.LogAction.UNFREEZE_PRODUCT,
                "手动解冻商品：" + Jdbc.strOf(product, "name") + "，恢复在售");
        return resultWithMessage(conn, productId, "商品已恢复在售，重新接收意向");
    }

    // --------------------------- 内部工具 ---------------------------

    public Map<String, Object> requireProduct(Connection conn, String productId) throws Exception {
        Map<String, Object> product = getProduct(conn, productId);
        if (product == null) {
            throw new NotFoundException("商品不存在");
        }
        return product;
    }

    public boolean hasActiveTransaction(Connection conn, String productId) throws Exception {
        return Jdbc.queryOne(conn,
                "SELECT id FROM buy_intent WHERE product_id = ? AND status = ? LIMIT 1",
                productId, Enums.IntentStatus.IN_TRANSACTION) != null;
    }

    public Map<String, Object> resultWithMessage(Connection conn, String productId, String message)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", fullById(conn, productId));
        result.put("message", message);
        return result;
    }

    public Map<String, Object> summary(Connection conn, String productId) throws Exception {
        Long failed = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM trade_record WHERE product_id = ? AND result = 'FAILED'", productId);
        Long buyers = Jdbc.scalarLong(conn,
                "SELECT COUNT(DISTINCT customer_id) AS c FROM buy_intent WHERE product_id = ?", productId);
        Map<String, Object> last = Jdbc.queryOne(conn,
                "SELECT * FROM trade_record WHERE product_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1",
                productId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("failed_rounds", failed == null ? 0 : failed.intValue());
        summary.put("buyer_count", buyers == null ? 0 : buyers.intValue());
        summary.put("final_result", last == null ? null : Jdbc.strOf(last, "result"));
        summary.put("final_result_label", last == null ? "未成交"
                : Enums.TradeResult.label(Jdbc.strOf(last, "result")));
        summary.put("last_transaction_at", last == null ? null : Jdbc.strOf(last, "created_at"));
        return summary;
    }

    private Map<String, Object> pageOf(Connection conn, String where, List<Object> params,
                                       int page, int pageSize) throws Exception {
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM product p WHERE " + where, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                SELECT_PRODUCT + "WHERE " + where + " ORDER BY p.published_at DESC, p.rowid DESC LIMIT ? OFFSET ?",
                pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(full(conn, row));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    private void appendFilters(Connection conn, StringBuilder where, List<Object> params,
                               String keyword, String categoryId) throws Exception {
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND p.name LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        if (categoryId != null && !categoryId.trim().isEmpty()) {
            Map<String, Object> category = categories.getCategory(conn, categoryId.trim());
            if (category != null && Jdbc.intOf(category, "level") == CategoryService.LEVEL_TOP) {
                where.append(" AND p.category_id IN (SELECT id FROM category WHERE parent_id = ?)");
            } else {
                where.append(" AND p.category_id = ?");
            }
            params.add(categoryId.trim());
        }
    }

    private Map<String, Object> customerOf(Map<String, Object> row) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", Jdbc.strOf(row, "customer_id"));
        customer.put("username", Jdbc.strOf(row, "username"));
        customer.put("phone", Jdbc.strOf(row, "phone"));
        customer.put("trade_location", Jdbc.strOf(row, "trade_location"));
        customer.put("registered_at", Jdbc.strOf(row, "customer_created_at"));
        return customer;
    }
}
