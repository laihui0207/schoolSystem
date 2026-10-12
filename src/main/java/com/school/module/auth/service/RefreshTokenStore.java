package com.school.module.auth.service;

import com.school.common.constant.CommonConstants;
import com.school.security.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌登记表（Redis）
 * <p>
 * 两类 key：{@code refresh:<jwt> -> userId}（刷新接口校验用）与
 * {@code user:refresh:<userId> -> 当前 refresh jwt}（按用户整体作废用，单活跃）。
 * TTL 与 app.jwt.refresh-expiration 同源。
 * <p>
 * ponytail: /auth/refresh 放行白名单后，refresh token 若不在服务端登记，登出/改密/禁用都无法终止会话，
 * 泄露的 refresh token 可无限重放直到自然过期。
 *
 * @author School System
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 登记新签发的刷新令牌，并作废该用户之前的刷新令牌（单活跃）
     */
    public void register(Long userId, String refreshToken) {
        revokeByUserId(userId);
        long ttl = jwtTokenUtil.getRefreshExpirationMillis();
        redisTemplate.opsForValue().set(CommonConstants.REFRESH_TOKEN_KEY + refreshToken, userId, ttl, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(CommonConstants.USER_REFRESH_KEY + userId, refreshToken, ttl, TimeUnit.MILLISECONDS);
    }

    /**
     * 轮换：旧刷新令牌用过即作废，新令牌登记为该用户唯一活跃刷新令牌
     */
    public void rotate(String oldRefreshToken, Long userId, String newRefreshToken) {
        redisTemplate.delete(CommonConstants.REFRESH_TOKEN_KEY + oldRefreshToken);
        register(userId, newRefreshToken);
    }

    /**
     * 刷新令牌是否仍在登记中（未被登出/改密/禁用/轮换作废）
     */
    public boolean isActive(String refreshToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CommonConstants.REFRESH_TOKEN_KEY + refreshToken));
    }

    /**
     * 作废该用户当前的刷新令牌（登出 / 重置密码 / 禁用 / 删除时调用）
     */
    public void revokeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        String userKey = CommonConstants.USER_REFRESH_KEY + userId;
        Object current = redisTemplate.opsForValue().get(userKey);
        if (current != null) {
            redisTemplate.delete(CommonConstants.REFRESH_TOKEN_KEY + current);
        }
        redisTemplate.delete(userKey);
    }

}
