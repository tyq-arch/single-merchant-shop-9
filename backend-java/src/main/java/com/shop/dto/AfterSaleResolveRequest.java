package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 卖家记录售后处理结果（FR-052）：协商中 → 已解决，退款结果需提供退款金额。 */
public class AfterSaleResolveRequest {
    public String result;

    public String note;

    @JsonProperty("refund_amount")
    public Double refundAmount;
}