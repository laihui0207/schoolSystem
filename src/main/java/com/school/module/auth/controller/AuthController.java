package com.school.module.auth.controller;

import com.school.common.result.Result;
import com.school.module.auth.dto.CaptchaResponse;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.auth.dto.PhoneLoginRequest;
import com.school.module.auth.dto.PublicSchoolResponse;
import com.school.module.auth.dto.RegisterRequest;
import com.school.module.auth.dto.SmsSendRequest;
import com.school.module.auth.dto.SmsSendResponse;
import com.school.module.auth.service.AuthService;
import com.school.module.log.annotation.OperationLog;
import com.school.module.sms.config.SmsProperties;
import com.school.module.sms.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 * <p>
 * 本控制器的全部端点都在 SecurityConfig.WHITE_LIST（logout 除外），不加 @PreAuthorize：
 * 它们恰恰发生在拿到 token 之前。
 *
 * @author School System
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsCodeService smsCodeService;
    private final SmsProperties smsProperties;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 发送短信验证码（scene: login | register | reset）
     */
    @PostMapping("/sms/send")
    @OperationLog(value = "发送短信验证码", module = "认证")
    public Result<SmsSendResponse> sendSmsCode(@Valid @RequestBody SmsSendRequest request,
                                               HttpServletRequest httpRequest) {
        long expiresIn = smsCodeService.sendCode(request.getPhone(), request.getScene(), resolveClientIp(httpRequest));
        return Result.success(SmsSendResponse.builder()
                .expiresIn(expiresIn)
                .sendInterval(smsProperties.getSendInterval())
                .build());
    }

    /**
     * 手机号 + 短信验证码登录
     */
    @PostMapping("/login/phone")
    @OperationLog(value = "手机验证码登录", module = "认证")
    public Result<LoginResponse> loginByPhone(@Valid @RequestBody PhoneLoginRequest request) {
        return Result.success(authService.loginByPhone(request));
    }

    /**
     * 学生 / 家长自助注册，成功即登录
     */
    @PostMapping("/register")
    @OperationLog(value = "自助注册", module = "认证")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 注册页学校下拉（匿名可读，仅 id + 名称）
     */
    @GetMapping("/schools")
    public Result<List<PublicSchoolResponse>> listPublicSchools() {
        return Result.success(authService.listPublicSchools());
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            authService.logout(token);
        }
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    @GetMapping("/captcha")
    public Result<CaptchaResponse> getCaptcha() {
        CaptchaResponse response = authService.generateCaptcha();
        return Result.success(response);
    }

    /**
     * 来源 IP：与 OperationLogAspect 同一套取法（XFF 首跳 > X-Real-IP > remoteAddr）
     * <p>
     * ponytail: 只用于单 IP 发码日限，XFF 可伪造意味着这道限流是"防误用"不是"防对抗"，手机号维度的限制才是硬约束
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

}
