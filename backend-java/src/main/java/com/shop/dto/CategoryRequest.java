package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 类别新增/重命名请求：parent_id 为空表示一级类别。 */
public class CategoryRequest {
    public String name;

    @JsonProperty("parent_id")
    public String parentId;
}
