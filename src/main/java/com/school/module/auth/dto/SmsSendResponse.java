package com.school.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发送短信验证码响应：前端据此做倒计时与有效期提示
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码有效期（秒）
     */
    private Long expiresIn;

    /**
     * 下次可发送的间隔（秒）
     */
    private Long sendInterval;

}
