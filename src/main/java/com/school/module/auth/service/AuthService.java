package com.school.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.dto.CaptchaResponse;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.config.service.ScoreRuleService;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.model.LoginUser;
import com.school.security.util.JwtTokenUtil;
import com.school.security.util.SecurityUtils;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final SysUserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenStore refreshTokenStore;
    private final ScoreRuleService scoreRuleService;

    @Value("${app.captcha.enabled:true}")
    private Boolean captchaEnabled;

    @Value("${app.captcha.expire:300}")
    private Long captchaExpire;

    @Value("${app.jwt.expiration}")
    private Long jwtExpiration;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 验证验证码
        if (captchaEnabled) {
            validateCaptcha(request.getCaptchaKey(), request.getCaptcha());
        }

        try {
            // 执行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 查询用户信息
            SysUser user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, request.getUsername())
                            .eq(SysUser::getDeleted, CommonConstants.NOT_DELETED)
            );

            if (user == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }

            // ponytail: 维护模式检查放在密码校验之后——"系统维护中"只回给凭证正确的用户，
            // 不会变成探测账号是否存在的信道；判断管理员要用 sys_user.user_type，也只有到这里才拿得到。
            // BusinessException 不是 AuthenticationException，不会被下面的 catch 吞成 LOGIN_FAIL。
            if (scoreRuleService.maintenanceMode() && !isAdminAccount(user)) {
                log.warn("维护模式拒绝登录: username={}, userType={}", user.getUsername(), user.getUserType());
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "系统维护中，请稍后再试");
            }

            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);

            // 生成Token
            String accessToken = jwtTokenUtil.generateToken(user.getId(), user.getUsername());
            String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUsername());

            // 将Token存入Redis
            storeToken(accessToken, user.getId());
            // ponytail: refresh token 同步登记（契约 T），否则登出后它仍能在已放行的 /auth/refresh 换出新会话
            refreshTokenStore.register(user.getId(), refreshToken);

            // 构建用户信息：studentId / teacherId / headTeacher 只有认证主体 LoginUser 才有
            LoginResponse.UserInfo userInfo = buildUserInfo(user, asLoginUser(authentication.getPrincipal()));

            // 构建响应
            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtExpiration / 1000)
                    .userInfo(userInfo)
                    .build();

        } catch (AuthenticationException e) {
            log.error("登录失败: username={}, error={}", request.getUsername(), e.getMessage());
            throw new BusinessException(ResultCode.LOGIN_FAIL);
        }
    }

    /**
     * 是否管理员账号（超级管理员 / 校管理员）：维护模式下仍允许登录
     */
    private boolean isAdminAccount(SysUser user) {
        Integer userType = user.getUserType();
        return UserRole.SUPER_ADMIN.getUserType().equals(userType)
                || UserRole.ADMIN.getUserType().equals(userType);
    }

    /**
     * 用户登出：作废 access token 与该用户当前的 refresh token
     */
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String tokenKey = CommonConstants.TOKEN_KEY + token;
        redisTemplate.delete(tokenKey);
        // ponytail: 只删 access token 时，泄露的 refresh token 仍可在 /auth/refresh 让会话复活；登出必须连带作废
        refreshTokenStore.revokeByUserId(resolveLogoutUserId(token));
    }

    /**
     * 登出时的用户ID：登出接口受保护，正常从 SecurityContext 取；拿不到时退回解析 token 自身
     */
    private Long resolveLogoutUserId(String token) {
        if (SecurityUtils.isAuthenticated()) {
            return SecurityUtils.currentUserId();
        }
        try {
            return jwtTokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("登出时无法解析 token 对应用户，跳过 refresh token 作废: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        // ponytail: 必须校验 claims.type=refresh，否则任意未过期 access token 都能在此换出新 token 对
        if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        // ponytail: 必须仍在 Redis 登记中——登出/改密/禁用/轮换都会删掉登记，否则泄露的 refresh token 可无限重放
        if (!refreshTokenStore.isActive(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenUtil.getUserIdFromToken(refreshToken);
        String username = jwtTokenUtil.getUsernameFromToken(refreshToken);

        // 查询用户信息（已删除的账号不能续签）
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 重新加载认证主体：禁用账号会在此被拒绝，同时拿到 studentId / teacherId / headTeacher
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 生成新的Token
        String newAccessToken = jwtTokenUtil.generateToken(userId, username);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(userId, username);

        // 将新Token存入Redis
        storeToken(newAccessToken, userId);
        // ponytail: 轮换——旧 refresh token 用过即作废，避免同一个 refresh token 被反复兑换
        refreshTokenStore.rotate(refreshToken, userId, newRefreshToken);

        LoginResponse.UserInfo userInfo = buildUserInfo(user, asLoginUser(userDetails));

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .userInfo(userInfo)
                .build();
    }

    /**
     * 将访问令牌写入 Redis，TTL 与 JWT 过期时间同源
     */
    private void storeToken(String accessToken, Long userId) {
        // ponytail: TTL 取 app.jwt.expiration，不再写死 24h；JwtAuthenticationFilter 续期用同一个值
        String tokenKey = CommonConstants.TOKEN_KEY + accessToken;
        redisTemplate.opsForValue().set(tokenKey, userId, jwtTokenUtil.getExpirationMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 认证主体若为 LoginUser 则返回，否则返回 null（容错，不影响登录）
     */
    private LoginUser asLoginUser(Object principal) {
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 构建登录响应中的用户信息
     * <p>
     * 基础字段来自 sys_user；studentId / teacherId / headTeacher 来自 LoginUser，
     * 前端学生自查成绩一律用 studentId，而不是 sys_user.id。
     */
    private LoginResponse.UserInfo buildUserInfo(SysUser user, LoginUser loginUser) {
        UserRole role = UserRole.fromUserType(user.getUserType());
        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .userType(user.getUserType())
                .schoolId(user.getSchoolId())
                .studentId(loginUser != null ? loginUser.getStudentId() : null)
                .teacherId(loginUser != null ? loginUser.getTeacherId() : null)
                .headTeacher(loginUser != null && loginUser.isHeadTeacher())
                .role(role != null ? role.getCode() : null)
                .build();
    }

    /**
     * 生成验证码
     */
    public CaptchaResponse generateCaptcha() {
        // 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String code = captcha.text().toLowerCase();
        String image = captcha.toBase64();

        // 生成Key
        String key = UUID.randomUUID().toString();

        // 存入Redis
        String captchaKey = CommonConstants.CAPTCHA_CODE_KEY + key;
        redisTemplate.opsForValue().set(captchaKey, code, captchaExpire, TimeUnit.SECONDS);

        return new CaptchaResponse(key, image, captchaExpire);
    }

    /**
     * 验证验证码
     */
    private void validateCaptcha(String key, String code) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        String captchaKey = CommonConstants.CAPTCHA_CODE_KEY + key;
        Object cachedCode = redisTemplate.opsForValue().get(captchaKey);

        if (cachedCode == null) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }

        if (!code.toLowerCase().equals(cachedCode.toString().toLowerCase())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        // 验证成功后删除验证码
        redisTemplate.delete(captchaKey);
    }

}
