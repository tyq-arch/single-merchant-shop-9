package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 修改密码请求。 */
public class ChangePasswordRequest {
    @JsonProperty("old_password")
    public String oldPassword;

    @JsonProperty("new_password")
    public String newPassword;
}
