package com.school.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求DTO
 *
 * @author School System
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码（captcha.enabled=false 时可传任意值）
     */
    private String captcha;

    /**
     * 验证码Key（captcha.enabled=false 时可传任意值）
     */
    private String captchaKey;

}
