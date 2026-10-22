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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交易管理：与队首进入交易并自动冻结；成功扣减库存；
 * 失败在同一动作内决定失败者作废或重新排到队尾（无自动递补、无次数限制）。
 */
@Service
public class TransactionService {

    private final ProductService products;
    private final QueueService queue;
    private final LogService logs;

    public TransactionService(ProductService products, QueueService queue, LogService logs) {
        this.products = products;
        this.queue = queue;
        this.logs = logs;
    }

    public Map<String, Object> start(Connection conn, String sellerId, String productId) throws Exception {
        Map<String, Object> product = products.requireProduct(conn, productId);
        String status = Jdbc.strOf(product, "status");
        if (Enums.ProductStatus.OFF_SHELF.equals(status)) {
            throw new ConflictException("商品已下架，无法进入交易");
        }
        if (Enums.ProductStatus.SOLD_OUT.equals(status)) {
            throw new ConflictException("商品已售罄，无法进入交易");
        }
        if (products.hasActiveTransaction(conn, productId)) {
            throw new ConflictException("已有进行中的交易，请先标记交易结果");
        }
        if (Jdbc.intOf(product, "stock") <= 0) {
            throw new ConflictException("商品库存为 0，无法进入交易");
        }
        Map<String, Object> head = queue.head(conn, productId);
        if (head == null) {
            throw new ConflictException("当前队列为空，没有可交易的意向购买人");
        }

        String intentId = Jdbc.strOf(head, "intent_id");
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "UPDATE buy_intent SET status = ?, queue_position = 1, updated_at = ? WHERE id = ?",
                Enums.IntentStatus.IN_TRANSACTION, now, intentId);
        Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                Enums.ProductStatus.FROZEN, now, productId);
        logs.logAction(conn, sellerId, Enums.LogAction.START_TRANSACTION,
                "与队列第 1 位进入交易，意向 " + intentId + "，商品自动冻结");

        Map<String, Object> intent = Jdbc.queryOne(conn, "SELECT * FROM buy_intent WHERE id = ?", intentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", intentWithCustomer(conn, intent));
        result.put("product", products.fullById(conn, productId));
        result.put("message", "已与队列第一位进入交易，商品自动冻结，队列已封口");
        return result;
    }

    public Map<String, Object> mark(Connection conn, String sellerId, String intentId, String result,
                                    String note, String failedAction) throws Exception {
        Map<String, Object> intent = Jdbc.queryOne(conn, "SELECT * FROM buy_intent WHERE id = ?", intentId);
        if (intent == null) {
            throw new NotFoundException("意向单不存在");
        }
        if (!Enums.IntentStatus.IN_TRANSACTION.equals(Jdbc.strOf(intent, "status"))) {
            throw new ConflictException("仅进行中的交易可以标记结果");
        }
        if (!Enums.TradeResult.SUCCESS.equals(result) && !Enums.TradeResult.FAILED.equals(result)) {
            throw new ApiException(422, "VALIDATION_ERROR", "result 只能为 SUCCESS 或 FAILED");
        }
        String productId = Jdbc.strOf(intent, "product_id");
        String now = Utils.nowIso();

        Jdbc.update(conn,
                "INSERT INTO trade_record (id, intent_id, product_id, customer_id, result, note, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Utils.newId(), intentId, productId, Jdbc.strOf(intent, "customer_id"), result, note, now);
        queue.remove(conn, productId, intentId);

        String message;
        if (Enums.TradeResult.SUCCESS.equals(result)) {
            int stock = Math.max(0, Jdbc.intOf(Jdbc.queryOne(conn,
                    "SELECT stock FROM product WHERE id = ?", productId), "stock") - 1);
            Jdbc.update(conn, "UPDATE buy_intent SET status = ?, updated_at = ? WHERE id = ?",
                    Enums.IntentStatus.SUCCESS, now, intentId);
            if (stock == 0) {
                Jdbc.update(conn, "UPDATE product SET stock = 0, status = ?, updated_at = ? WHERE id = ?",
                        Enums.ProductStatus.SOLD_OUT, now, productId);
                voidWaitingIntents(conn, productId, Enums.IntentStatus.SOLD_OUT, now);
                message = "交易成功，库存已扣减至 0，商品自动下架，剩余排队意向已售罄作废";
            } else {
                Jdbc.update(conn, "UPDATE product SET stock = ?, status = ?, updated_at = ? WHERE id = ?",
                        stock, Enums.ProductStatus.ON_SALE, now, productId);
                message = "交易成功，库存已扣减，商品恢复在售";
            }
            logs.logAction(conn, sellerId, Enums.LogAction.TRANSACTION_SUCCESS,
                    "意向 " + intentId + " 交易成功：" + message);
        } else {
            if (failedAction == null || failedAction.trim().isEmpty()) {
                throw new ApiException(422, "VALIDATION_ERROR",
                        "交易失败时必须同时选择失败者处理方式（failed_action: VOID 或 REQUEUE）");
            }
            String action = failedAction.trim().toUpperCase();
            if (Enums.FailedAction.VOID.equals(action)) {
                Jdbc.update(conn, "UPDATE buy_intent SET status = ?, updated_at = ? WHERE id = ?",
                        Enums.IntentStatus.VOIDED, now, intentId);
                message = "交易失败，库存不变，商品恢复在售；该买家意向已作废";
            } else if (Enums.FailedAction.REQUEUE.equals(action)) {
                int position = queue.enqueue(conn, productId, intentId, now);
                Jdbc.update(conn,
                        "UPDATE buy_intent SET status = ?, requeue_count = requeue_count + 1, "
                                + "queue_position = ?, updated_at = ? WHERE id = ?",
                        Enums.IntentStatus.REQUEUED, position, now, intentId);
                message = "交易失败，库存不变，商品恢复在售；该买家已重新排到队尾（第 " + position + " 位）";
            } else {
                throw new ApiException(422, "VALIDATION_ERROR", "failed_action 只能为 VOID 或 REQUEUE");
            }
            Jdbc.update(conn, "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                    Enums.ProductStatus.ON_SALE, now, productId);
            logs.logAction(conn, sellerId, Enums.LogAction.TRANSACTION_FAILED,
                    "意向 " + intentId + " 交易失败：" + message);
        }

        Map<String, Object> updated = Jdbc.queryOne(conn, "SELECT * FROM buy_intent WHERE id = ?", intentId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("intent", intentWithCustomer(conn, updated));
        response.put("product", products.fullById(conn, productId));
        response.put("message", message);
        return response;
    }

    private void voidWaitingIntents(Connection conn, String productId, String status, String now)
            throws Exception {
        Jdbc.update(conn,
                "UPDATE buy_intent SET status = ?, updated_at = ? WHERE product_id = ? AND status IN (?, ?)",
                status, now, productId, Enums.IntentStatus.QUEUED, Enums.IntentStatus.REQUEUED);
        Jdbc.update(conn, "DELETE FROM queue_item WHERE product_id = ?", productId);
    }

    private Map<String, Object> intentWithCustomer(Connection conn, Map<String, Object> intent)
            throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT i.*, cu.username, cu.phone, cu.trade_location, cu.created_at AS customer_created_at "
                        + "FROM buy_intent i JOIN customer cu ON cu.id = i.customer_id WHERE i.id = ?",
                Jdbc.strOf(intent, "id"));
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", Jdbc.strOf(row, "customer_id"));
        customer.put("username", Jdbc.strOf(row, "username"));
        customer.put("phone", Jdbc.strOf(row, "phone"));
        customer.put("trade_location", Jdbc.strOf(row, "trade_location"));
        customer.put("registered_at", Jdbc.strOf(row, "customer_created_at"));
        return Serializers.intent(row, customer);
    }
}
