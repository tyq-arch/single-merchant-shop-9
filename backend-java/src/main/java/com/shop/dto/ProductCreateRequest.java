package com.shop.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 发布商品请求（多商品、二级类别、库存、多图、富媒体描述）。 */
public class ProductCreateRequest {
    public String name;
    public Double price;
    public String description;
    public Integer stock;

    @JsonProperty("category_id")
    public String categoryId;

    @JsonProperty("image_urls")
    public List<String> imageUrls;
}
