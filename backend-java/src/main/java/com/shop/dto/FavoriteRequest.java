package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 收藏商品请求。 */
public class FavoriteRequest {
    @JsonProperty("product_id")
    public String productId;
}
