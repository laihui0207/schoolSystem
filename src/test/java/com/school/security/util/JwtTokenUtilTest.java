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
    @DisplayName("应该区分刷新令牌与访问令牌")
    void shouldDistinguishRefreshTokenFromAccessToken() {
        // Given
        String accessToken = jwtTokenUtil.generateToken(1L, "testuser");
        String refreshToken = jwtTokenUtil.generateRefreshToken(1L, "testuser");

        // When & Then: 只有带 type=refresh 声明的令牌才能用于刷新
        assertFalse(jwtTokenUtil.isRefreshToken(accessToken));
        assertTrue(jwtTokenUtil.isRefreshToken(refreshToken));
    }

    @Test
    @DisplayName("无效令牌不应被识别为刷新令牌")
    void shouldNotTreatInvalidTokenAsRefreshToken() {
        assertFalse(jwtTokenUtil.isRefreshToken("invalid.token.here"));
    }

    @Test
    @DisplayName("同一用户同一秒内两次签发的刷新令牌必须不同")
    void shouldIssueDistinctRefreshTokens_WhenSameUserSameSecond() {
        // Given & When: iat/exp 秒级精度下连续签发
        String first = jwtTokenUtil.generateRefreshToken(1L, "testuser");
        String second = jwtTokenUtil.generateRefreshToken(1L, "testuser");

        // Then: 否则第二次登录会把第一次轮换刚作废的 Redis 登记重新激活，旧 refresh token 复活
        assertNotEquals(first, second);
        assertTrue(jwtTokenUtil.isRefreshToken(first));
        assertTrue(jwtTokenUtil.isRefreshToken(second));
    }

    @Test
    @DisplayName("应该暴露配置的访问令牌有效期")
    void shouldExposeConfiguredExpiration() {
        assertEquals(3600000L, jwtTokenUtil.getExpirationMillis());
    }

    @Test
    @DisplayName("应该暴露配置的刷新令牌有效期")
    void shouldExposeConfiguredRefreshExpiration() {
        assertEquals(86400000L, jwtTokenUtil.getRefreshExpirationMillis());
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
