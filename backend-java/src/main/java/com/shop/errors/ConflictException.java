package com.shop.errors;

/** 状态冲突（409），例如重复发布、冻结中提交意向等。 */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }
}
