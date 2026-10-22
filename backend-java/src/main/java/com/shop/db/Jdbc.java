package com.shop.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 JDBC 助手：查询结果统一映射为 LinkedHashMap，便于直接序列化。
 * 列名保持数据库中的下划线命名，与 Python 版响应字段一致。
 */
public final class Jdbc {

    private Jdbc() {
    }

    public static List<Map<String, Object>> query(Connection conn, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static Map<String, Object> queryOne(Connection conn, String sql, Object... args)
            throws SQLException {
        List<Map<String, Object>> rows = query(conn, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    public static Long scalarLong(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Object value = rs.getObject(1);
                return value == null ? null : ((Number) value).longValue();
            }
        }
    }

    public static long longOf(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static int intOf(Map<String, Object> row, String key) {
        if (row == null) {
            return 0;
        }
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static Integer integerOrNull(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    public static String strOf(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }
}
