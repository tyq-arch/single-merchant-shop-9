package com.shop.web;

import com.shop.dto.PaymentCallbackRequest;
import com.shop.services.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付网关回调（模拟）：校验 HMAC-SHA256 签名并幂等处理（FR-041 / NFR-013）。
 * 该接口由支付渠道调用，无需客户登录令牌。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody PaymentCallbackRequest body) {
        return payments.callback(body);
    }
}
