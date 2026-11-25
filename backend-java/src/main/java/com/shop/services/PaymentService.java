package com.shop.services;

import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.dto.PayRequest;
import com.shop.dto.PaymentCallbackRequest;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.errors.UnauthorizedException;
import com.shop.security.SecurityService;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;
import com.shop.web.Validation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 线上支付（FR-039 ~ FR-042）：待支付订单发起支付，本期对接模拟网关同步返回结果；
 * 回调接口校验 HMAC 签名并保证幂等（NFR-013 / NFR-014）。
 */
@Service
public class PaymentService {

    private static final DateTimeFormatter PAY_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderService orders;
    private final SecurityService security;
    private final Db db;

    public PaymentService(OrderService orders, SecurityService security, Db db) {
        this.orders = orders;
        this.security = security;
        this.db = db;
    }

    /** 发起支付：成功则订单转「已支付」；失败保持「待支付」，可重新发起（FR-042）。 */
    public Map<String, Object> pay(Connection conn, String customerId, String orderId, PayRequest body)
            throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn,
                "SELECT * FROM shop_order WHERE id = ? AND customer_id = ?", orderId, customerId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        String status = Jdbc.strOf(order, "status");
        if (!Enums.OrderStatus.PENDING_PAYMENT.equals(status)) {
            throw new ConflictException("订单已「" + Enums.OrderStatus.label(status) + "」，无需重复支付");
        }
        if (body == null) {
            throw Validation.error("body", "body", "请求体不能为空");
        }
        String method = body.paymentMethod == null ? "" : body.paymentMethod.trim().toUpperCase();
        if (!Enums.PaymentMethod.ALL.contains(method)) {
            throw Validation.error("body", "payment_method",
                    "支付方式需为 BANK_CARD / CREDIT_CARD / ALIPAY / WECHAT");
        }
        String outcome = body.simulateResult == null || body.simulateResult.trim().isEmpty()
                ? Enums.PaymentStatus.SUCCESS : body.simulateResult.trim().toUpperCase();
        if (!Enums.PaymentStatus.SUCCESS.equals(outcome) && !Enums.PaymentStatus.FAILED.equals(outcome)) {
            throw Validation.error("body", "simulate_result", "模拟支付结果需为 SUCCESS 或 FAILED");
        }

        String now = Utils.nowIso();
        int amountCents = Jdbc.intOf(order, "total_cents");
        String paymentId = Utils.newId();
        String paymentNo = nextPaymentNo(conn);
        boolean success = Enums.PaymentStatus.SUCCESS.equals(outcome);
        String transactionNo = success ? "TXN" + Utils.newId().substring(0, 20).toUpperCase() : null;

        Jdbc.update(conn,
                "INSERT INTO payment (id, order_id, payment_no, method, amount_cents, status, transaction_no, "
                        + "failure_reason, paid_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                paymentId, orderId, paymentNo, method, amountCents, outcome, transactionNo,
                success ? null : "支付渠道返回失败（模拟）", success ? now : null, now, now);

        if (success) {
            Jdbc.update(conn,
                    "UPDATE shop_order SET status = ?, paid_at = ?, updated_at = ? "
                            + "WHERE id = ? AND status = ?",
                    Enums.OrderStatus.PAID, now, now, orderId, Enums.OrderStatus.PENDING_PAYMENT);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payment", Serializers.payment(Jdbc.queryOne(conn, "SELECT * FROM payment WHERE id = ?", paymentId)));
        result.put("order", orders.detailOf(conn, customerId, orderId));
        result.put("message", success ? "支付成功，等待商家确认" : "支付失败，订单保持待支付，可重新发起支付");
        return result;
    }

    /**
     * 支付结果回调（模拟网关）：签名不匹配返回 401；重复回调幂等返回当前状态。
     */
    public Map<String, Object> callback(PaymentCallbackRequest body) {
        if (body == null || body.paymentId == null || body.paymentId.trim().isEmpty()
                || body.status == null || body.status.trim().isEmpty()) {
            throw Validation.error("body", "payment_id", "回调缺少支付单号或状态");
        }
        String paymentId = body.paymentId.trim();
        String status = body.status.trim().toUpperCase();
        if (!Enums.PaymentStatus.SUCCESS.equals(status) && !Enums.PaymentStatus.FAILED.equals(status)) {
            throw Validation.error("body", "status", "回调状态需为 SUCCESS 或 FAILED");
        }
        String transactionNo = body.transactionNo == null ? "" : body.transactionNo.trim();
        String expected = security.hmacHex(paymentId + "|" + status + "|" + transactionNo);
        if (body.signature == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                body.signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("支付回调签名校验失败");
        }

        return db.transaction(conn -> {
            Map<String, Object> payment = Jdbc.queryOne(conn, "SELECT * FROM payment WHERE id = ?", paymentId);
            if (payment == null) {
                throw new NotFoundException("支付记录不存在");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            if (Enums.PaymentStatus.SUCCESS.equals(Jdbc.strOf(payment, "status"))) {
                result.put("payment", Serializers.payment(payment));
                result.put("idempotent", true);
                result.put("message", "支付已成功，重复回调已忽略");
                return result;
            }
            String now = Utils.nowIso();
            Jdbc.update(conn,
                    "UPDATE payment SET status = ?, transaction_no = ?, paid_at = ?, updated_at = ? WHERE id = ?",
                    status, transactionNo.isEmpty() ? null : transactionNo,
                    Enums.PaymentStatus.SUCCESS.equals(status) ? now : null, now, paymentId);
            if (Enums.PaymentStatus.SUCCESS.equals(status)) {
                Jdbc.update(conn,
                        "UPDATE shop_order SET status = ?, paid_at = ?, updated_at = ? "
                                + "WHERE id = ? AND status = ?",
                        Enums.OrderStatus.PAID, now, now, Jdbc.strOf(payment, "order_id"),
                        Enums.OrderStatus.PENDING_PAYMENT);
            }
            result.put("payment", Serializers.payment(
                    Jdbc.queryOne(conn, "SELECT * FROM payment WHERE id = ?", paymentId)));
            result.put("idempotent", false);
            result.put("message", "回调处理完成");
            return result;
        });
    }

    /** 支付流水号：PAY + 日期 + 当天自增序号。 */
    private String nextPaymentNo(Connection conn) throws Exception {
        String day = Utils.now().format(PAY_DAY);
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM payment WHERE payment_no LIKE ?", "PAY" + day + "-%");
        int sequence = (count == null ? 0 : count.intValue()) + 1;
        return "PAY" + day + "-" + String.format("%04d", sequence);
    }
}
