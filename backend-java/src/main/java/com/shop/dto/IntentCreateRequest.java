package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 提交购买意向请求：客户登录后仅需指定商品。 */
public class IntentCreateRequest {
    @JsonProperty("product_id")
    public String productId;
}
