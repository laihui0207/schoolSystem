package com.school.security.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT工具类测试
 *
 * @author School System
 */
@DisplayName("JWT工具类测试")
class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        // 使用反射设置私有字段
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-secret-key-for-unit-testing-minimum-length-required");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600000L);
        ReflectionTestUtils.setField(jwtTokenUtil, "refreshExpiration", 86400000L);
    }

    @Test
    @DisplayName("应该成功生成访问令牌")
    void shouldGenerateAccessToken() {
        // Given
        Long userId = 1L;
        String username = "testuser";

        // When
        String token = jwtTokenUtil.generateToken(userId, username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("应该成功生成刷新令牌")
    void shouldGenerateRefreshToken() {
        // Given
        Long userId = 1L;
        String username = "testuser";

        // When
        String token = jwtTokenUtil.generateRefreshToken(userId, username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("应该从令牌中正确提取用户ID")
    void shouldExtractUserIdFromToken() {
        // Given
        Long userId = 123L;
        String username = "testuser";
        String token = jwtTokenUtil.generateToken(userId, username);

        // When
        Long extractedUserId = jwtTokenUtil.getUserIdFromToken(token);

        // Then
        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("应该从令牌中正确提取用户名")
    void shouldExtractUsernameFromToken() {
        // Given
        Long userId = 1L;
        String username = "testuser";
        String token = jwtTokenUtil.generateToken(userId, username);

        // When
        String extractedUsername = jwtTokenUtil.getUsernameFromToken(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("应该正确验证有效令牌")
    void shouldValidateValidToken() {
        // Given
        Long userId = 1L;
        String username = "testuser";
        String token = jwtTokenUtil.generateToken(userId, username);

        // When
        boolean isValid = jwtTokenUtil.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("应该正确识别过期令牌")
    void shouldIdentifyExpiredToken() {
        // Given - 创建一个立即过期的token
        JwtTokenUtil expiredTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(expiredTokenUtil, "secret", "test-secret-key-for-unit-testing-minimum-length-required");
        ReflectionTestUtils.setField(expiredTokenUtil, "expiration", -1000L); // 负数表示已过期
        ReflectionTestUtils.setField(expiredTokenUtil, "refreshExpiration", 86400000L);

        String token = expiredTokenUtil.generateToken(1L, "testuser");

        // When
        boolean isExpired = jwtTokenUtil.isTokenExpired(token);

        // Then
        assertTrue(isExpired);
    }

    @Test
    @DisplayName("应该拒绝无效令牌")
    void shouldRejectInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = jwtTokenUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

}
