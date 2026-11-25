package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发起支付请求（FR-039 / FR-040）。
 *
 * <p>{@code simulate_result} 仅用于本期模拟支付网关：SUCCESS（默认）或 FAILED，
 * 用于演示「支付失败保持待支付、可重新发起」（FR-042）。
 */
public class PayRequest {
    @JsonProperty("payment_method")
    public String paymentMethod;

    @JsonProperty("simulate_result")
    public String simulateResult;
}
