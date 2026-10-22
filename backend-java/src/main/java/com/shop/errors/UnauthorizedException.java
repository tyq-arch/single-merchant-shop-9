package com.shop.errors;

/** 未登录或凭证无效（401）。 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(401, "UNAUTHORIZED", message);
    }
}
