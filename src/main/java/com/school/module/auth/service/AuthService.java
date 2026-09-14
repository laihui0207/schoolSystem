package com.school.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.dto.CaptchaResponse;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.JwtTokenUtil;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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

            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);

            // 生成Token
            String accessToken = jwtTokenUtil.generateToken(user.getId(), user.getUsername());
            String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUsername());

            // 将Token存入Redis
            String tokenKey = CommonConstants.TOKEN_KEY + accessToken;
            redisTemplate.opsForValue().set(tokenKey, user.getId(), 24, TimeUnit.HOURS);

            // 构建用户信息
            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .avatar(user.getAvatar())
                    .userType(user.getUserType())
                    .schoolId(user.getSchoolId())
                    .build();

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
     * 用户登出
     */
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            String tokenKey = CommonConstants.TOKEN_KEY + token;
            redisTemplate.delete(tokenKey);
        }
    }

    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        Long userId = jwtTokenUtil.getUserIdFromToken(refreshToken);
        String username = jwtTokenUtil.getUsernameFromToken(refreshToken);

        // 生成新的Token
        String newAccessToken = jwtTokenUtil.generateToken(userId, username);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(userId, username);

        // 将新Token存入Redis
        String tokenKey = CommonConstants.TOKEN_KEY + newAccessToken;
        redisTemplate.opsForValue().set(tokenKey, userId, 24, TimeUnit.HOURS);

        // 查询用户信息
        SysUser user = userMapper.selectById(userId);

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .userType(user.getUserType())
                .schoolId(user.getSchoolId())
                .build();

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .userInfo(userInfo)
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
