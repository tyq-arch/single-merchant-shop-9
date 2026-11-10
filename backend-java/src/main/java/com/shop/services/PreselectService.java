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

/**
 * 挑战B 商品预选：收藏、购物车、游客临时预选与登录合并。
 *
 * <p>规则：收藏/购物车均不锁库存；同一客户对同一商品各仅一条；
 * 收藏 ≤200 条、购物车 ≤50 条、单条数量 ≤99（TBD-11）。
 */
@Service
public class PreselectService {

    public static final int MAX_FAVORITES = 200;
    public static final int MAX_CART_ITEMS = 50;
    public static final int MAX_QUANTITY = 99;

    private static final String FAVORITE_TABLE = "favorite";
    private static final String GUEST_FAVORITE_TABLE = "guest_favorite";
    private static final String CART_TABLE = "cart_item";
    private static final String GUEST_CART_TABLE = "guest_cart_item";

    private final ProductService products;

    public PreselectService(ProductService products) {
        this.products = products;
    }

    // --------------------------- 收藏 ---------------------------

    public Map<String, Object> addFavorite(Connection conn, String ownerId, boolean guest, String productId)
            throws Exception {
        requireOnSaleProduct(conn, productId, "收藏");
        String table = guest ? GUEST_FAVORITE_TABLE : FAVORITE_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";

        Map<String, Object> existing = Jdbc.queryOne(conn,
                "SELECT id FROM " + table + " WHERE " + ownerColumn + " = ? AND product_id = ?",
                ownerId, productId);
        if (existing != null) {
            throw new ConflictException("该商品已在收藏中");
        }
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM " + table + " WHERE " + ownerColumn + " = ?", ownerId);
        if (count != null && count >= MAX_FAVORITES) {
            throw new ConflictException("收藏数量已达上限 " + MAX_FAVORITES + " 件，请先移除部分收藏");
        }

        Jdbc.update(conn,
                "INSERT INTO " + table + " (id, " + ownerColumn + ", product_id, created_at) VALUES (?, ?, ?, ?)",
                Utils.newId(), ownerId, productId, Utils.nowIso());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("favorite", favoriteItem(conn, productId));
        result.put("message", "已加入收藏");
        return result;
    }

    public Map<String, Object> removeFavorite(Connection conn, String ownerId, boolean guest, String productId)
            throws Exception {
        String table = guest ? GUEST_FAVORITE_TABLE : FAVORITE_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        int removed = Jdbc.update(conn,
                "DELETE FROM " + table + " WHERE " + ownerColumn + " = ? AND product_id = ?", ownerId, productId);
        if (removed == 0) {
            throw new NotFoundException("收藏中不存在该商品");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已取消收藏");
        return result;
    }

    public Map<String, Object> listFavorites(Connection conn, String ownerId, boolean guest,
                                             int page, int pageSize) throws Exception {
        String table = guest ? GUEST_FAVORITE_TABLE : FAVORITE_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM " + table + " WHERE " + ownerColumn + " = ?", ownerId);
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM " + table + " WHERE " + ownerColumn + " = ? "
                        + "ORDER BY created_at DESC, rowid DESC LIMIT ? OFFSET ?",
                ownerId, pageSize, (page - 1) * pageSize);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> product = loadProductBrief(conn, Jdbc.strOf(row, "product_id"));
            Map<String, Object> item = Serializers.favorite(row);
            item.put("product", product);
            item.put("available", product.get("available"));
            item.put("unavailable_reason", product.get("unavailable_reason"));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    // --------------------------- 购物车 ---------------------------

    public Map<String, Object> addCart(Connection conn, String ownerId, boolean guest,
                                       String productId, int quantity) throws Exception {
        requireOnSaleProduct(conn, productId, "加购");
        if (products.availableQuantity(conn, productId) <= 0) {
            throw new ConflictException("商品已无货，无法加入购物车");
        }
        String table = guest ? GUEST_CART_TABLE : CART_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        String now = Utils.nowIso();
        Map<String, Object> existing = Jdbc.queryOne(conn,
                "SELECT * FROM " + table + " WHERE " + ownerColumn + " = ? AND product_id = ?",
                ownerId, productId);
        if (existing == null) {
            Long count = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM " + table + " WHERE " + ownerColumn + " = ?", ownerId);
            if (count != null && count >= MAX_CART_ITEMS) {
                throw new ConflictException("购物车条目已达上限 " + MAX_CART_ITEMS + " 件，请先移除部分商品");
            }
            Jdbc.update(conn,
                    "INSERT INTO " + table + " (id, " + ownerColumn + ", product_id, quantity, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Utils.newId(), ownerId, productId, quantity, now, now);
        } else {
            int merged = Jdbc.intOf(existing, "quantity") + quantity;
            if (merged > MAX_QUANTITY) {
                throw new ConflictException("单条购物车数量不能超过 " + MAX_QUANTITY + " 件");
            }
            Jdbc.update(conn,
                    "UPDATE " + table + " SET quantity = ?, updated_at = ? WHERE id = ?",
                    merged, now, Jdbc.strOf(existing, "id"));
        }
        return cartResult(conn, ownerId, guest, "已加入购物车");
    }

    public Map<String, Object> updateCart(Connection conn, String ownerId, boolean guest,
                                          String productId, int quantity) throws Exception {
        String table = guest ? GUEST_CART_TABLE : CART_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        Map<String, Object> existing = Jdbc.queryOne(conn,
                "SELECT id FROM " + table + " WHERE " + ownerColumn + " = ? AND product_id = ?",
                ownerId, productId);
        if (existing == null) {
            throw new NotFoundException("购物车中不存在该商品");
        }
        Jdbc.update(conn, "UPDATE " + table + " SET quantity = ?, updated_at = ? WHERE id = ?",
                quantity, Utils.nowIso(), Jdbc.strOf(existing, "id"));
        return cartResult(conn, ownerId, guest, "购物车已更新");
    }

    public Map<String, Object> removeCart(Connection conn, String ownerId, boolean guest, String productId)
            throws Exception {
        String table = guest ? GUEST_CART_TABLE : CART_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        int removed = Jdbc.update(conn,
                "DELETE FROM " + table + " WHERE " + ownerColumn + " = ? AND product_id = ?", ownerId, productId);
        if (removed == 0) {
            throw new NotFoundException("购物车中不存在该商品");
        }
        return cartResult(conn, ownerId, guest, "已从购物车移除");
    }

    public Map<String, Object> listCart(Connection conn, String ownerId, boolean guest) throws Exception {
        String table = guest ? GUEST_CART_TABLE : CART_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM " + table + " WHERE " + ownerColumn + " = ? "
                        + "ORDER BY created_at ASC, rowid ASC", ownerId);
        List<Map<String, Object>> items = new ArrayList<>();
        long totalQuantity = 0;
        long totalCents = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> product = loadProductBrief(conn, Jdbc.strOf(row, "product_id"));
            Integer priceCents = (Integer) product.get("price_cents");
            Map<String, Object> item = Serializers.cartItem(row, priceCents);
            item.put("product", product);
            item.put("available", product.get("available"));
            item.put("unavailable_reason", product.get("unavailable_reason"));
            items.add(item);
            totalQuantity += Jdbc.intOf(row, "quantity");
            if (priceCents != null) {
                totalCents += (long) priceCents * Jdbc.intOf(row, "quantity");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("item_count", items.size());
        result.put("total_quantity", totalQuantity);
        result.put("total", Utils.priceFromCents((int) totalCents));
        result.put("total_cents", (int) totalCents);
        return result;
    }

    /** 购物车多选转收藏（FR-024）：收藏成功后移除原购物车项（TBD-06）。 */
    public Map<String, Object> moveToFavorites(Connection conn, String ownerId, boolean guest,
                                               List<String> productIds) throws Exception {
        String cartTable = guest ? GUEST_CART_TABLE : CART_TABLE;
        String favoriteTable = guest ? GUEST_FAVORITE_TABLE : FAVORITE_TABLE;
        String ownerColumn = guest ? "guest_id" : "customer_id";

        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> cartItems = new ArrayList<>();
        for (String productId : productIds) {
            Map<String, Object> row = Jdbc.queryOne(conn,
                    "SELECT * FROM " + cartTable + " WHERE " + ownerColumn + " = ? AND product_id = ?",
                    ownerId, productId);
            if (row == null) {
                missing.add(productId);
            } else {
                cartItems.add(row);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConflictException("以下商品不在购物车中：" + String.join(", ", missing));
        }

        Long favoriteCount = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM " + favoriteTable + " WHERE " + ownerColumn + " = ?", ownerId);
        int current = favoriteCount == null ? 0 : favoriteCount.intValue();
        List<String> added = new ArrayList<>();
        String now = Utils.nowIso();
        for (Map<String, Object> row : cartItems) {
            String productId = Jdbc.strOf(row, "product_id");
            Map<String, Object> exists = Jdbc.queryOne(conn,
                    "SELECT id FROM " + favoriteTable + " WHERE " + ownerColumn + " = ? AND product_id = ?",
                    ownerId, productId);
            if (exists == null) {
                if (current >= MAX_FAVORITES) {
                    throw new ConflictException("收藏数量已达上限 " + MAX_FAVORITES + " 件，请先移除部分收藏");
                }
                Jdbc.update(conn,
                        "INSERT INTO " + favoriteTable + " (id, " + ownerColumn + ", product_id, created_at) "
                                + "VALUES (?, ?, ?, ?)",
                        Utils.newId(), ownerId, productId, now);
                current++;
                added.add(productId);
            }
            Jdbc.update(conn, "DELETE FROM " + cartTable + " WHERE id = ?", Jdbc.strOf(row, "id"));
        }

        Map<String, Object> result = cartResult(conn, ownerId, guest,
                "已将 " + cartItems.size() + " 件商品转为收藏");
        result.put("added_to_favorites", added);
        result.put("moved_count", cartItems.size());
        return result;
    }

    /** 登录后合并游客临时预选（TBD-12）：收藏去重、购物车累加（数量封顶 99）。 */
    public Map<String, Object> mergeGuest(Connection conn, String customerId, String guestId) throws Exception {
        int favoritesAdded = 0;
        int cartMerged = 0;

        List<Map<String, Object>> guestFavorites = Jdbc.query(conn,
                "SELECT * FROM guest_favorite WHERE guest_id = ? ORDER BY created_at ASC, rowid ASC", guestId);
        Long favoriteCount = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM favorite WHERE customer_id = ?", customerId);
        int currentFavorites = favoriteCount == null ? 0 : favoriteCount.intValue();
        String now = Utils.nowIso();
        for (Map<String, Object> row : guestFavorites) {
            String productId = Jdbc.strOf(row, "product_id");
            Map<String, Object> exists = Jdbc.queryOne(conn,
                    "SELECT id FROM favorite WHERE customer_id = ? AND product_id = ?", customerId, productId);
            if (exists == null && currentFavorites < MAX_FAVORITES) {
                Jdbc.update(conn,
                        "INSERT INTO favorite (id, customer_id, product_id, created_at) VALUES (?, ?, ?, ?)",
                        Utils.newId(), customerId, productId, now);
                currentFavorites++;
                favoritesAdded++;
            }
        }
        Jdbc.update(conn, "DELETE FROM guest_favorite WHERE guest_id = ?", guestId);

        List<Map<String, Object>> guestCart = Jdbc.query(conn,
                "SELECT * FROM guest_cart_item WHERE guest_id = ? ORDER BY created_at ASC, rowid ASC", guestId);
        Long cartCount = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM cart_item WHERE customer_id = ?", customerId);
        int currentCart = cartCount == null ? 0 : cartCount.intValue();
        for (Map<String, Object> row : guestCart) {
            String productId = Jdbc.strOf(row, "product_id");
            int quantity = Jdbc.intOf(row, "quantity");
            Map<String, Object> exists = Jdbc.queryOne(conn,
                    "SELECT * FROM cart_item WHERE customer_id = ? AND product_id = ?", customerId, productId);
            if (exists == null) {
                if (currentCart >= MAX_CART_ITEMS) {
                    continue;
                }
                Jdbc.update(conn,
                        "INSERT INTO cart_item (id, customer_id, product_id, quantity, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        Utils.newId(), customerId, productId, Math.min(quantity, MAX_QUANTITY), now, now);
                currentCart++;
            } else {
                int merged = Math.min(Jdbc.intOf(exists, "quantity") + quantity, MAX_QUANTITY);
                Jdbc.update(conn, "UPDATE cart_item SET quantity = ?, updated_at = ? WHERE id = ?",
                        merged, now, Jdbc.strOf(exists, "id"));
            }
            cartMerged++;
        }
        Jdbc.update(conn, "DELETE FROM guest_cart_item WHERE guest_id = ?", guestId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("favorites_added", favoritesAdded);
        result.put("cart_merged", cartMerged);
        return result;
    }

    // --------------------------- 内部工具 ---------------------------

    private Map<String, Object> cartResult(Connection conn, String ownerId, boolean guest, String message)
            throws Exception {
        Map<String, Object> result = listCart(conn, ownerId, guest);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> requireOnSaleProduct(Connection conn, String productId, String action)
            throws Exception {
        Map<String, Object> product = products.getProduct(conn, productId);
        if (product == null) {
            throw new NotFoundException("商品不存在");
        }
        String status = Jdbc.strOf(product, "status");
        if (Enums.ProductStatus.OFF_SHELF.equals(status)) {
            throw new ConflictException("商品已下架，无法" + action);
        }
        if (Enums.ProductStatus.SOLD_OUT.equals(status)) {
            throw new ConflictException("商品已售罄，无法" + action);
        }
        if (Enums.ProductStatus.FROZEN.equals(status)) {
            throw new ConflictException("商品交易中，暂不可" + action);
        }
        return product;
    }

    /** 商品简要信息：名称、单价、可售库存、状态与下架/无货标注（FR-019）。 */
    private Map<String, Object> loadProductBrief(Connection conn, String productId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT p.*, (SELECT url FROM product_image WHERE product_id = p.id "
                        + "ORDER BY sort_order ASC, rowid ASC LIMIT 1) AS primary_image "
                        + "FROM product p WHERE p.id = ?", productId);
        Map<String, Object> brief = new LinkedHashMap<>();
        if (row == null) {
            brief.put("id", productId);
            brief.put("name", null);
            brief.put("price", null);
            brief.put("price_cents", null);
            brief.put("stock", 0);
            brief.put("available_stock", 0);
            brief.put("status", null);
            brief.put("status_label", "已删除");
            brief.put("available", false);
            brief.put("unavailable_reason", "已下架");
            brief.put("primary_image", null);
            return brief;
        }
        String status = Jdbc.strOf(row, "status");
        int availableStock = products.availableOf(conn, row);
        boolean available = Enums.ProductStatus.ON_SALE.equals(status) && availableStock > 0;
        brief.put("id", Jdbc.strOf(row, "id"));
        brief.put("name", Jdbc.strOf(row, "name"));
        brief.put("price", Utils.priceFromCents(Jdbc.integerOrNull(row, "price_cents")));
        brief.put("price_cents", Jdbc.integerOrNull(row, "price_cents"));
        brief.put("stock", availableStock);
        brief.put("available_stock", availableStock);
        brief.put("status", status);
        brief.put("status_label", Enums.ProductStatus.label(status));
        brief.put("category_id", Jdbc.strOf(row, "category_id"));
        brief.put("primary_image", Jdbc.strOf(row, "primary_image"));
        brief.put("available", available);
        brief.put("unavailable_reason", unavailableReason(status));
        return brief;
    }

    private Map<String, Object> favoriteItem(Connection conn, String productId) throws Exception {
        Map<String, Object> brief = loadProductBrief(conn, productId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("product_id", productId);
        item.put("product", brief);
        item.put("available", brief.get("available"));
        item.put("unavailable_reason", brief.get("unavailable_reason"));
        return item;
    }

    private static String unavailableReason(String status) {
        if (Enums.ProductStatus.OFF_SHELF.equals(status)) {
            return "已下架";
        }
        if (Enums.ProductStatus.SOLD_OUT.equals(status)) {
            return "无货";
        }
        if (Enums.ProductStatus.FROZEN.equals(status)) {
            return "交易中";
        }
        return null;
    }

    /** 去重并保留顺序。 */
    public static List<String> distinctIds(List<String> productIds) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (productIds != null) {
            for (String id : productIds) {
                if (id != null && !id.trim().isEmpty()) {
                    set.add(id.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }
}
