package com.school.module.rbac.service;

import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.UserRolesUpdateRequest;

import java.util.List;

/**
 * 用户附加角色服务
 * <p>
 * sys_user.user_type 仍是主判定（SecurityUtils.isXxx），这里维护的是 sys_user_role 中的「附加角色」，
 * 用于给用户叠加自定义角色的权限点。
 *
 * @author School System
 */
public interface UserRoleService {

    List<RoleResponse> getUserRoles(Long userId);

    /** 全量设置；与 user_type 对应的内置角色始终保留，其他内置角色拒绝 */
    List<RoleResponse> updateUserRoles(Long userId, UserRolesUpdateRequest request);

}
