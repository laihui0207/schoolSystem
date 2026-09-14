package com.school.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码工具类测试
 *
 * @author School System
 */
@DisplayName("密码工具类测试")
class PasswordUtilTest {

    @Test
    @DisplayName("应该成功加密密码")
    void shouldEncodePassword() {
        // Given
        String rawPassword = "password123";

        // When
        String encodedPassword = PasswordUtil.encode(rawPassword);

        // Then
        assertNotNull(encodedPassword);
        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(encodedPassword.startsWith("$2a$"));
    }

    @Test
    @DisplayName("应该正确验证匹配的密码")
    void shouldMatchCorrectPassword() {
        // Given
        String rawPassword = "password123";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        // When
        boolean matches = PasswordUtil.matches(rawPassword, encodedPassword);

        // Then
        assertTrue(matches);
    }

    @Test
    @DisplayName("应该拒绝不匹配的密码")
    void shouldRejectIncorrectPassword() {
        // Given
        String rawPassword = "password123";
        String wrongPassword = "wrongpassword";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        // When
        boolean matches = PasswordUtil.matches(wrongPassword, encodedPassword);

        // Then
        assertFalse(matches);
    }

    @Test
    @DisplayName("相同密码每次加密结果应该不同")
    void shouldGenerateDifferentHashForSamePassword() {
        // Given
        String password = "password123";

        // When
        String encoded1 = PasswordUtil.encode(password);
        String encoded2 = PasswordUtil.encode(password);

        // Then
        assertNotEquals(encoded1, encoded2);
        assertTrue(PasswordUtil.matches(password, encoded1));
        assertTrue(PasswordUtil.matches(password, encoded2));
    }

}
