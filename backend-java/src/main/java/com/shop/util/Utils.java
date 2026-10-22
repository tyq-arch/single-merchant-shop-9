package com.shop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通用工具函数。
 */
public final class Utils {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Utils() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    public static String nowIso() {
        return now().format(ISO_FORMAT);
    }

    public static String toIso(LocalDateTime value) {
        return value == null ? null : value.format(ISO_FORMAT);
    }

    public static LocalDateTime parseIso(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ISO_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    /** 手机号脱敏：保留前 3 位与后 4 位（FR-019 / NFR-003）。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "***";
    }

    /** 价格（元）转分，四舍五入。 */
    public static int centsFromPrice(double price) {
        return BigDecimal.valueOf(price)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** 分转价格（元）。 */
    public static Double priceFromCents(Integer cents) {
        if (cents == null) {
            return null;
        }
        return cents / 100.0;
    }
}
