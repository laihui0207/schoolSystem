package com.school.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 验证码响应DTO
 *
 * @author School System
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码Key
     */
    private String captchaKey;

    /**
     * 验证码图片Base64
     */
    private String captchaImage;

    /**
     * 过期时间（秒）
     */
    private Long expiresIn;

}
