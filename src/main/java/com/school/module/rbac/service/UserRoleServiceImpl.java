package com.school.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.UserRolesUpdateRequest;
import com.school.module.rbac.mapper.SysRoleMapper;
import com.school.module.user.entity.SysRole;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户附加角色服务实现
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<RoleResponse> getUserRoles(Long userId) {
        getVisibleUser(userId);
        return roleMapper.selectByUserId(userId).stream()
                .map(role -> RoleConverter.toResponse(role, null, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RoleResponse> updateUserRoles(Long userId, UserRolesUpdateRequest request) {
        SysUser user = getVisibleUser(userId);
        UserRole base = UserRole.fromUserType(user.getUserType());
        String baseCode = base == null ? null : base.getRoleCode();

        Set<String> target = new LinkedHashSet<>();
        if (baseCode != null) {
            target.add(baseCode);
        }
        Set<String> requested = request.getRoleCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String code : requested) {
            if (code.equals(baseCode)) {
                continue;
            }
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (role == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在：" + code);
            }
            // ponytail: 内置角色与 user_type 一一对应，且 role_code 会直接成为 authority 命中 hasRole('SUPER_ADMIN') 等表达式。
            // 校管理员若能给教师附加 ROLE_SCHOOL_ADMIN，就绕过了 UserController 「只有超管能改用户类型」的限制，所以一律拒绝
            if (RoleConverter.isBuiltin(role)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "内置角色由用户类型决定，不能作为附加角色：" + code);
            }
            assertAssignable(role, user);
            target.add(code);
        }

        userMapper.deleteUserRolesByUserId(userId);
        for (String code : target) {
            if (userMapper.insertUserRole(userId, code) == 0) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在：" + code);
            }
        }
        return getUserRoles(userId);
    }

    /**
     * 非超管只能看本校用户；越界当作不存在，不泄露 id 是否存在（与 UserServiceImpl.getUserById 一致）
     */
    private SysUser getVisibleUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!SecurityUtils.isSuperAdmin() && !Objects.equals(user.getSchoolId(), SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 自定义角色的可分配范围：
     * 超管——平台级角色或与用户同校的角色；
     * 校管理员——只能分配本校自建角色（平台级自定义角色的权限集由超管定义，可能超出校管理员自身持有的权限）
     */
    private void assertAssignable(SysRole role, SysUser user) {
        if (SecurityUtils.isSuperAdmin()) {
            if (role.getSchoolId() != null && !role.getSchoolId().equals(user.getSchoolId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "角色与用户不属于同一学校：" + role.getRoleCode());
            }
            return;
        }
        if (role.getSchoolId() == null || !role.getSchoolId().equals(SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能分配本校自建角色：" + role.getRoleCode());
        }
    }

}
