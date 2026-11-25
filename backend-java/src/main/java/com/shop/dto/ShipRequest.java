package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 卖家发货请求（TBD-10）：填写物流服务商与运单号，订单由「备货完成」进入「开始发货」。 */
public class ShipRequest {
    public String carrier;

    @JsonProperty("tracking_no")
    public String trackingNo;
}