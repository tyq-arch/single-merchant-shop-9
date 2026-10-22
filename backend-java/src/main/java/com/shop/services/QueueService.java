package com.shop.services;

import com.shop.db.Jdbc;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 每商品独立队列：严格按入队顺序先到先得，位次连续且从 1 开始。
 */
@Service
public class QueueService {

    /** 入队到队尾，返回排队位次。 */
    public int enqueue(Connection conn, String productId, String intentId, String createdAt) throws Exception {
        Long max = Jdbc.scalarLong(conn,
                "SELECT COALESCE(MAX(position), 0) AS max_pos FROM queue_item WHERE product_id = ?", productId);
        int position = (max == null ? 0 : max.intValue()) + 1;
        Jdbc.update(conn,
                "INSERT INTO queue_item (intent_id, product_id, position, created_at) VALUES (?, ?, ?, ?)",
                intentId, productId, position, createdAt);
        Jdbc.update(conn, "UPDATE buy_intent SET queue_position = ? WHERE id = ?", position, intentId);
        return position;
    }

    /** 移出队列并重排剩余位次。 */
    public void remove(Connection conn, String productId, String intentId) throws Exception {
        Jdbc.update(conn, "DELETE FROM queue_item WHERE intent_id = ?", intentId);
        renumber(conn, productId);
    }

    /** 位次连续化，并同步意向单上的 queue_position。 */
    public void renumber(Connection conn, String productId) throws Exception {
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT intent_id FROM queue_item WHERE product_id = ? ORDER BY position ASC, created_at ASC",
                productId);
        int index = 1;
        for (Map<String, Object> row : rows) {
            String intentId = Jdbc.strOf(row, "intent_id");
            Jdbc.update(conn, "UPDATE queue_item SET position = ? WHERE intent_id = ?", index, intentId);
            Jdbc.update(conn, "UPDATE buy_intent SET queue_position = ? WHERE id = ?", index, intentId);
            index++;
        }
    }

    /** 队首（位次最小）。 */
    public Map<String, Object> head(Connection conn, String productId) throws Exception {
        return Jdbc.queryOne(conn,
                "SELECT * FROM queue_item WHERE product_id = ? ORDER BY position ASC LIMIT 1", productId);
    }

    public Integer positionOf(Connection conn, String intentId, String productId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT position FROM queue_item WHERE intent_id = ? AND product_id = ?", intentId, productId);
        return row == null ? null : Jdbc.integerOrNull(row, "position");
    }

    public int count(Connection conn, String productId) throws Exception {
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM queue_item WHERE product_id = ?", productId);
        return count == null ? 0 : count.intValue();
    }

    public void clear(Connection conn, String productId) throws Exception {
        Jdbc.update(conn, "DELETE FROM queue_item WHERE product_id = ?", productId);
    }
}
