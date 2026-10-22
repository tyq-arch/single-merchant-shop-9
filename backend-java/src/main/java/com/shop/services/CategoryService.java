package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ApiException;
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
 * 二级类别树：仅支持新增、重命名、删除；无移动；有商品的类别禁止删除。
 */
@Service
public class CategoryService {

    public static final int LEVEL_TOP = 1;
    public static final int LEVEL_LEAF = 2;

    private final LogService logs;

    public CategoryService(LogService logs) {
        this.logs = logs;
    }

    public Map<String, Object> getCategory(Connection conn, String id) throws Exception {
        return Jdbc.queryOne(conn, "SELECT * FROM category WHERE id = ?", id);
    }

    /** 商品必须归属二级类别（FR-003）。 */
    public Map<String, Object> requireLeaf(Connection conn, String id) throws Exception {
        Map<String, Object> category = getCategory(conn, id);
        if (category == null) {
            throw new NotFoundException("类别不存在");
        }
        if (Jdbc.intOf(category, "level") != LEVEL_LEAF) {
            throw new ApiException(422, "VALIDATION_ERROR", "商品必须归属二级类别（小类）");
        }
        return category;
    }

    public List<Map<String, Object>> tree(Connection conn) throws Exception {
        List<Map<String, Object>> tops = Jdbc.query(conn,
                "SELECT * FROM category WHERE level = 1 ORDER BY created_at ASC, rowid ASC");
        List<Map<String, Object>> leaves = Jdbc.query(conn, categoryWithCountSql() + " WHERE c.level = 2 "
                + "ORDER BY c.created_at ASC, c.rowid ASC");
        Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
        for (Map<String, Object> leaf : leaves) {
            String parentId = Jdbc.strOf(leaf, "parent_id");
            children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(Serializers.category(leaf));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> top : tops) {
            Map<String, Object> item = Serializers.category(top);
            List<Map<String, Object>> sub = children.get(Jdbc.strOf(top, "id"));
            item.put("children", sub == null ? new ArrayList<Map<String, Object>>() : sub);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> create(Connection conn, String sellerId, String name, String parentId)
            throws Exception {
        int level;
        String normalizedParent = null;
        if (parentId == null || parentId.trim().isEmpty()) {
            level = LEVEL_TOP;
        } else {
            Map<String, Object> parent = getCategory(conn, parentId);
            if (parent == null) {
                throw new NotFoundException("上级类别不存在");
            }
            if (Jdbc.intOf(parent, "level") != LEVEL_TOP) {
                throw new ApiException(422, "VALIDATION_ERROR", "类别仅支持两级，不能在二级类别下继续新增");
            }
            normalizedParent = parentId;
            level = LEVEL_LEAF;
        }
        Map<String, Object> duplicate = Jdbc.queryOne(conn,
                "SELECT id FROM category WHERE name = ? AND COALESCE(parent_id, '') = ?",
                name, normalizedParent == null ? "" : normalizedParent);
        if (duplicate != null) {
            throw new ConflictException("同级下已存在同名类别");
        }
        String id = Utils.newId();
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "INSERT INTO category (id, parent_id, name, level, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, normalizedParent, name, level, now, now);
        logs.logAction(conn, sellerId, Enums.LogAction.CREATE_CATEGORY, "新增类别：" + name);
        return getCategory(conn, id);
    }

    public Map<String, Object> rename(Connection conn, String sellerId, String id, String name)
            throws Exception {
        Map<String, Object> category = getCategory(conn, id);
        if (category == null) {
            throw new NotFoundException("类别不存在");
        }
        String parentId = Jdbc.strOf(category, "parent_id");
        Map<String, Object> duplicate = Jdbc.queryOne(conn,
                "SELECT id FROM category WHERE name = ? AND COALESCE(parent_id, '') = ? AND id <> ?",
                name, parentId == null ? "" : parentId, id);
        if (duplicate != null) {
            throw new ConflictException("同级下已存在同名类别");
        }
        Jdbc.update(conn, "UPDATE category SET name = ?, updated_at = ? WHERE id = ?",
                name, Utils.nowIso(), id);
        logs.logAction(conn, sellerId, Enums.LogAction.RENAME_CATEGORY,
                "类别重命名：" + Jdbc.strOf(category, "name") + " -> " + name);
        return getCategory(conn, id);
    }

    public void delete(Connection conn, String sellerId, String id) throws Exception {
        Map<String, Object> category = getCategory(conn, id);
        if (category == null) {
            throw new NotFoundException("类别不存在");
        }
        Long children = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM category WHERE parent_id = ?", id);
        if (children != null && children > 0) {
            throw new ConflictException("该类别下还有子类别，无法删除");
        }
        Long products = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM product WHERE category_id = ?", id);
        if (products != null && products > 0) {
            throw new ConflictException("该类别下还有商品，无法删除");
        }
        Jdbc.update(conn, "DELETE FROM category WHERE id = ?", id);
        logs.logAction(conn, sellerId, Enums.LogAction.DELETE_CATEGORY,
                "删除类别：" + Jdbc.strOf(category, "name"));
    }

    private String categoryWithCountSql() {
        return "SELECT c.*, (SELECT COUNT(*) FROM product p WHERE p.category_id = c.id) AS product_count "
                + "FROM category c";
    }
}
