package com.school.module.rbac.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新角色请求；字段为 null 表示不修改
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateRequest {

    /**
     * 角色编码；内置角色不允许修改
     */
    @Size(max = 50, message = "角色编码不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_:\\-]*$", message = "角色编码只能包含字母、数字、下划线、冒号、中划线，且以字母开头")
    private String roleCode;

    @Size(max = 50, message = "角色名称不能超过50个字符")
    private String roleName;

    @Size(max = 200, message = "角色描述不能超过200个字符")
    private String description;

}
