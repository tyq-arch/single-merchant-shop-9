package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 标记交易结果请求：失败时必须在同一动作内选择失败者处理方式。 */
public class TransactionMarkRequest {
    public String result;
    public String note;

    @JsonProperty("failed_action")
    public String failedAction;
}
