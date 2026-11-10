package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 加入购物车请求：同一商品已存在时累加数量。 */
public class CartAddRequest {
    @JsonProperty("product_id")
    public String productId;

    public Integer quantity;
}
