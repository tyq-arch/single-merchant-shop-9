package com.shop.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.shop.errors.ValidationException;

/**
 * 参数校验：错误结构对齐 FastAPI 的 422 响应。
 */
public final class Validation {

    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s]{5,20}$");

    private Validation() {
    }

    public static ValidationException error(String location, String field, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        List<String> loc = new ArrayList<>();
        loc.add(location);
        loc.add(field);
        detail.put("loc", loc);
        detail.put("msg", message);
        detail.put("type", "value_error");
        return new ValidationException(message, Collections.singletonList(detail));
    }

    /** 文本必填 + 长度限制 + 去首尾空白。 */
    public static String text(String value, String location, String field, int min, int max, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw error(location, field, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() < min) {
            throw error(location, field, label + "长度不能少于 " + min + " 个字符");
        }
        if (trimmed.length() > max) {
            throw error(location, field, label + "长度不能超过 " + max + " 个字符");
        }
        return trimmed;
    }

    /** 密码校验：≥8 位且满足强度（大写/小写/数字/特殊字符至少 3 类）（C-29 / NFR-003）。 */
    public static String password(String value, String location, String field, String label) {
        String pwd = text(value, location, field, 8, 128, label);
        int classes = 0;
        if (pwd.matches(".*[A-Z].*")) {
            classes++;
        }
        if (pwd.matches(".*[a-z].*")) {
            classes++;
        }
        if (pwd.matches(".*[0-9].*")) {
            classes++;
        }
        if (pwd.matches(".*[^A-Za-z0-9].*")) {
            classes++;
        }
        if (classes < 3) {
            throw error(location, field,
                    label + "强度不足：至少包含大写字母、小写字母、数字、特殊字符中的 3 类，且不少于 8 位");
        }
        return pwd;
    }

    public static void optionalLength(String value, String location, String field, int max, String label) {
        if (value != null && value.length() > max) {
            throw error(location, field, label + "长度不能超过 " + max + " 个字符");
        }
    }

    public static void phone(String value, String location, String field) {
        if (value == null || !PHONE.matcher(value).matches()) {
            throw error(location, field, "联系电话格式不正确");
        }
    }

    /** 价格校验并转换为分：大于 0、不超过 9999999、最多两位小数。 */
    public static int priceCents(Double value, String location, String field) {
        if (value == null) {
            throw error(location, field, "价格不能为空");
        }
        if (value <= 0) {
            throw error(location, field, "价格必须大于 0");
        }
        if (value > 9_999_999d) {
            throw error(location, field, "价格不能超过 9999999");
        }
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value);
        if (decimal.stripTrailingZeros().scale() > 2) {
            throw error(location, field, "价格最多保留两位小数");
        }
        return decimal.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    /** 库存校验：必填且为非负整数。 */
    public static int stock(Integer value, String location, String field) {
        if (value == null) {
            throw error(location, field, "库存不能为空");
        }
        if (value < 0) {
            throw error(location, field, "库存不能为负数");
        }
        return value;
    }

    public static int page(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 1;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw error("query", "page", "page 必须为整数");
        }
        if (value < 1) {
            throw error("query", "page", "page 必须大于等于 1");
        }
        return value;
    }

    public static int pageSize(String raw, int defaultSize, int maxSize) {
        if (raw == null || raw.isEmpty()) {
            return defaultSize;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw error("query", "page_size", "page_size 必须为整数");
        }
        if (value < 1 || value > maxSize) {
            throw error("query", "page_size", "page_size 必须在 1 到 " + maxSize + " 之间");
        }
        return value;
    }
}
