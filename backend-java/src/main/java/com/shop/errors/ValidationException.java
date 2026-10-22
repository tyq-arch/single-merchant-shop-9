package com.shop.errors;

import java.util.List;
import java.util.Map;

/** 请求参数校验失败（422），details 与 FastAPI 的错误结构保持一致。 */
public class ValidationException extends ApiException {

    private final List<Map<String, Object>> details;

    public ValidationException(String message, List<Map<String, Object>> details) {
        super(422, "VALIDATION_ERROR", message);
        this.details = details;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }
}
