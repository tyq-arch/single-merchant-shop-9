package com.shop.errors;

import java.util.List;
import java.util.Map;

/**
 * 业务异常：由服务层抛出，统一转换为 {"error": {"code", "message", "details"}} 响应。
 *
 * <p>details 为可选的逐项明细（如下单缺货清单），无明细时为 null。
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<Map<String, Object>> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, List<Map<String, Object>> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 逐项明细，未提供时为 null。 */
    public List<Map<String, Object>> getDetails() {
        return details;
    }
}
