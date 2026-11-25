package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 收货地址新增/修改（FR-046 / FR-047）。 */
public class AddressRequest {
    @JsonProperty("receiver_name")
    public String receiverName;

    @JsonProperty("receiver_phone")
    public String receiverPhone;

    public String province;
    public String city;
    public String district;
    public String detail;

    @JsonProperty("postal_code")
    public String postalCode;

    @JsonProperty("is_default")
    public Boolean isDefault;
}
