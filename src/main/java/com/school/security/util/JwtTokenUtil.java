package com.school.security.util;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 *
 * @author School System
 */
@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    @Value("${app.jwt.refresh-expiration}")
    private Long refreshExpiration;

    private static final String CLAIM_TYPE = "type";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    /**
     * 生成访问令牌
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, expiration);
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put(CLAIM_TYPE, REFRESH_TOKEN_TYPE);
        // ponytail: iat/exp 只有秒级精度，同一用户一秒内两次登录会签出字节相同的 refresh token，
        // 第二次 register 会把第一次轮换刚删掉的 Redis 键重新登记，旧 token 复活。jti 保证每次签发唯一。
        claims.put("jti", java.util.UUID.randomUUID().toString());
        return createToken(claims, refreshExpiration);
    }

    /**
     * 创建令牌
     */
    private String createToken(Map<String, Object> claims, Long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从令牌中获取Claims
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("解析Token失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    /**
     * 从令牌中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 验证令牌是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 是否为刷新令牌（claims.type == refresh）
     * <p>
     * 访问令牌没有 type 声明，解析失败的令牌也视为非刷新令牌。
     */
    public boolean isRefreshToken(String token) {
        // ponytail: 刷新接口只校验签名/过期的话，access token 也能换新 token，刷新令牌的长有效期就失去意义
        try {
            Claims claims = getClaimsFromToken(token);
            return REFRESH_TOKEN_TYPE.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 访问令牌有效期（毫秒），供 Redis 中 token 记录的 TTL 使用
     */
    public Long getExpirationMillis() {
        // ponytail: Redis TTL 与 JWT 过期时间来自同一配置项，避免两处 24h 写死后改配置只生效一半
        return expiration;
    }

    /**
     * 刷新令牌有效期（毫秒），供 Redis 中 refresh token 登记的 TTL 使用
     */
    public Long getRefreshExpirationMillis() {
        // ponytail: refresh token 登记进 Redis 后 TTL 必须与 JWT 自身过期一致，登出/轮换语义才成立
        return refreshExpiration;
    }

    /**
     * 刷新令牌
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        return generateToken(userId, username);
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

}
