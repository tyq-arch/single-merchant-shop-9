package com.shop;

import com.shop.errors.ValidationException;
import com.shop.web.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 联系电话校验：手机号、座机、+86 前缀与分隔符。 */
class ValidationTest {

    @Test
    void acceptsAndNormalizesMobileNumbers() {
        assertEquals("13800001234", Validation.phone("13800001234", "body", "phone"));
        assertEquals("13800001234", Validation.phone("+86 138-0000-1234", "body", "phone"));
        assertEquals("13800001234", Validation.phone("86-138-0000-1234", "body", "phone"));
        assertEquals("13800001234", Validation.phone("138　0000　1234", "body", "phone"));
    }

    @Test
    void acceptsAndNormalizesLandlineNumbers() {
        assertEquals("01012345678", Validation.phone("010-12345678", "body", "phone"));
        assertEquals("01012345678", Validation.phone("（010）1234 5678", "body", "phone"));
        assertEquals("01012345678", Validation.phone("+86 (010) 1234 5678", "body", "phone"));
        assertEquals("07551234567", Validation.phone("0755-1234-567", "body", "phone"));
        assertEquals("12345678", Validation.phone("12345678", "body", "phone"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(ValidationException.class, () -> Validation.phone(null, "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("   ", "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("abc", "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("12345", "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("+86", "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("1380000123", "body", "phone"));
        assertThrows(ValidationException.class, () -> Validation.phone("010-123456", "body", "phone"));
    }

    /** 客户密码：≥8 位 + 大写/小写/数字/特殊字符至少 3 类（NFR-003 / C-29）。 */
    @Test
    void enforcesCustomerPasswordStrength() {
        assertEquals("Pass1234", Validation.strongPassword("Pass1234", "body", "password", "密码"));
        assertEquals("Abcdef1!", Validation.strongPassword("Abcdef1!", "body", "password", "密码"));
        assertEquals("中文密码测试Ab1", Validation.strongPassword("中文密码测试Ab1", "body", "password", "密码"));
        // 仅小写 + 数字（2 类）
        assertThrows(ValidationException.class,
                () -> Validation.strongPassword("pass1234", "body", "password", "密码"));
        // 仅数字（1 类）
        assertThrows(ValidationException.class,
                () -> Validation.strongPassword("12345678", "body", "password", "密码"));
        // 长度不足 8 位
        assertThrows(ValidationException.class,
                () -> Validation.strongPassword("Abc123", "body", "password", "密码"));
    }
}
