package com.school.security.filter;

import com.school.common.constant.CommonConstants;
import com.school.security.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证过滤器
 *
 * @author School System
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** 唯一允许通过 ?token= 传凭证的路径（ChatController 的 SSE 流式接口） */
    private static final String SSE_STREAM_PATH = "/chat/stream";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (StringUtils.hasText(token) && jwtTokenUtil.validateToken(token)) {
                // 检查Redis中是否存在该Token（用于退出登录）
                String tokenKey = CommonConstants.TOKEN_KEY + token;
                Boolean exists = redisTemplate.hasKey(tokenKey);

                if (Boolean.FALSE.equals(exists)) {
                    log.warn("Token已失效: {}", token);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtTokenUtil.getUsernameFromToken(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 刷新Token过期时间
                    // ponytail: TTL 跟随 app.jwt.expiration，不再写死 24h，与 AuthService 写入时一致
                    redisTemplate.expire(tokenKey, jwtTokenUtil.getExpirationMillis(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            log.error("JWT认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头或 query param 中提取Token
     * query param 支持 SSE（EventSource 不支持自定义 header）
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        // SSE fallback: ?token=xxx
        // ponytail: 只对 EventSource 用的流式端点放开 query 取 token。对所有端点放开会让
        // access token 进入服务器访问日志、浏览器历史与 Referer，可被重放。
        if (isSseStreamRequest(request)) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }

    private boolean isSseStreamRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith(SSE_STREAM_PATH);
    }

}
