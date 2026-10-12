package com.school.module.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户更新请求（字段为 null 表示不修改；username / password 不可经此接口修改）
 *
 * @author School System
 */
@Data
public class UserUpdateRequest {

    /**
     * 真实姓名
     */
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String realName;

    /**
     * 角色：super_admin / admin / teacher / student / parent
     */
    private String role;

    /**
     * 所属学校ID
     */
    private Long schoolId;

    /**
     * 手机号（传空串表示清空）
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

}
