package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 购物车多选操作（多选下单 / 多选转收藏）。 */
public class BatchProductRequest {
    @JsonProperty("product_ids")
    public List<String> productIds;
}
