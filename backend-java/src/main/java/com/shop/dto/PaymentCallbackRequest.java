package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 支付结果回调（模拟支付网关，FR-041）：需携带 HMAC-SHA256 签名，重复回调幂等。 */
public class PaymentCallbackRequest {
    @JsonProperty("payment_id")
    public String paymentId;

    @JsonProperty("transaction_no")
    public String transactionNo;

    public String status;

    public String signature;
}
