package com.school.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求
 *
 * @author School System
 */
@Data
public class UserPasswordRequest {

    /**
     * 新密码（明文，入库前 BCrypt 加密）
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在6-72位之间")
    private String password;

}
