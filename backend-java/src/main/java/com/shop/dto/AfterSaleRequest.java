package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 售后申请（FR-051）：针对已交易完成的订单项提交标题、描述与图片（图片选填，最多 9 张）。 */
public class AfterSaleRequest {
    @JsonProperty("order_item_id")
    public String orderItemId;

    public String title;
    public String description;

    @JsonProperty("image_urls")
    public List<String> imageUrls;
}
