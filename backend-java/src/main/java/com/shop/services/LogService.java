package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.util.Utils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 操作日志（FR-042 / NFR-010）。
 */
@Service
public class LogService {

    public void logAction(Connection conn, String sellerId, String action, String detail) throws Exception {
        Jdbc.update(conn,
                "INSERT INTO operation_log (id, seller_id, action, detail, created_at) VALUES (?, ?, ?, ?, ?)",
                Utils.newId(), sellerId, action, detail, Utils.nowIso());
    }

    /** 返回 {"items": [...], "total": n}。 */
    public Map<String, Object> listLogs(Connection conn, int page, int pageSize) throws Exception {
        Long total = Jdbc.scalarLong(conn, "SELECT COUNT(*) AS c FROM operation_log");
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM operation_log ORDER BY created_at DESC, rowid DESC LIMIT ? OFFSET ?",
                pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", total == null ? 0L : total);
        return result;
    }
}
