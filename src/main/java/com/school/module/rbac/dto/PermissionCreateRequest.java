package com.school.module.rbac.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建权限点请求（仅超管）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCreateRequest {

    /**
     * 权限编码，形如 module:action 或 module:sub:action
     */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码不能超过100个字符")
    @Pattern(regexp = "^[a-z][a-z0-9\\-]*(:[a-z][a-z0-9\\-]*)*$", message = "权限编码格式应为 module:action，小写字母/数字/中划线")
    private String permissionCode;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称不能超过100个字符")
    private String permissionName;

    /**
     * 资源类型: 1-菜单 2-按钮 3-接口
     */
    @NotNull(message = "资源类型不能为空")
    @Min(value = 1, message = "资源类型取值 1/2/3")
    @Max(value = 3, message = "资源类型取值 1/2/3")
    private Integer resourceType;

    @Size(max = 200, message = "URL不能超过200个字符")
    private String url;

    /** 父权限ID，null 为一级菜单 */
    private Long parentId;

    private Integer sortOrder;

}
