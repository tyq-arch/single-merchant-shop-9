package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 客户注册请求。 */
public class CustomerRegisterRequest {
    public String username;
    public String password;
    public String phone;

    @JsonProperty("trade_location")
    public String tradeLocation;
}
