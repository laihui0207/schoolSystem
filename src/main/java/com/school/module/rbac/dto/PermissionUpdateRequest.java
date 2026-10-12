package com.school.module.rbac.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新权限点请求（仅超管）；null 表示不修改。
 * ponytail: permission_code 不可改——它已经散落在角色绑定、前端 v-permission 与 @PreAuthorize 字面量里，改码等于悄悄撤权。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionUpdateRequest {

    @Size(max = 100, message = "权限名称不能超过100个字符")
    private String permissionName;

    @Min(value = 1, message = "资源类型取值 1/2/3")
    @Max(value = 3, message = "资源类型取值 1/2/3")
    private Integer resourceType;

    @Size(max = 200, message = "URL不能超过200个字符")
    private String url;

    /** 父权限ID；传 0 表示移到一级 */
    private Long parentId;

    private Integer sortOrder;

}
