package com.shop.errors;

import java.util.List;
import java.util.Map;

/** 状态冲突（409），例如重复操作、缺货、超出数量上限等。 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }

    /** 带逐项明细的冲突，如多选下单的缺货清单（TBD-04）。 */
    public ConflictException(String message, List<Map<String, Object>> details) {
        super(409, "CONFLICT", message, details);
    }
}
