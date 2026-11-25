package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 购物车多选下单请求：必须选择收货地址（未传时带出默认地址）。 */
public class CheckoutRequest {
    @JsonProperty("product_ids")
    public List<String> productIds;

    @JsonProperty("address_id")
    public String addressId;
}
