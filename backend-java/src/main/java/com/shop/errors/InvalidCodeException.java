package com.shop.errors;

/** 口令码无效或已失效（410）。 */
public class InvalidCodeException extends ApiException {
    public InvalidCodeException() {
        super(410, "COMMAND_CODE_INVALID", "口令码无效或已失效");
    }
}
