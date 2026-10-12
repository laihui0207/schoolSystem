package com.school.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.RoleCreateRequest;
import com.school.module.rbac.dto.RolePermissionsRequest;
import com.school.module.rbac.dto.RoleQueryRequest;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.RoleUpdateRequest;
import com.school.module.rbac.dto.RoleUserCount;
import com.school.module.rbac.entity.SysPermission;
import com.school.module.rbac.mapper.SysPermissionMapper;
import com.school.module.rbac.mapper.SysRoleMapper;
import com.school.module.rbac.mapper.SysRolePermissionMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.user.entity.SysRole;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现
 * <p>
 * sys_role 不在租户插件表清单内，跨校隔离在本类收口：校管理员可见平台级 + 本校角色，
 * 但只能改动本校自建角色——平台级/内置角色的改动会影响全平台。
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final String BUILTIN_PREFIX = "ROLE_";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SchoolMapper schoolMapper;

    @Override
    public Page<RoleResponse> listRoles(RoleQueryRequest request) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (SecurityUtils.isSuperAdmin()) {
            wrapper.eq(request.getSchoolId() != null, SysRole::getSchoolId, request.getSchoolId());
        } else {
            // ponytail: 非超管忽略客户端传入的 schoolId，固定为「平台级 OR 本校」
            Long schoolId = SecurityUtils.currentSchoolId();
            wrapper.and(w -> w.isNull(SysRole::getSchoolId).or().eq(SysRole::getSchoolId, schoolId));
        }
        wrapper.like(StringUtils.hasText(request.getRoleCode()), SysRole::getRoleCode, request.getRoleCode())
                .like(StringUtils.hasText(request.getRoleName()), SysRole::getRoleName, request.getRoleName())
                .eq(request.getIsBuiltin() != null, SysRole::getIsBuiltin, request.getIsBuiltin())
                .orderByDesc(SysRole::getIsBuiltin)
                .orderByAsc(SysRole::getId);

        Page<SysRole> page = roleMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        List<SysRole> roles = page.getRecords();
        Map<Long, Long> userCounts = userCountsByRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toSet()));
        Map<Long, String> schoolNames = schoolNamesByIds(roles.stream()
                .map(SysRole::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet()));

        Page<RoleResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(roles.stream()
                .map(r -> RoleConverter.toResponse(r, schoolNames.get(r.getSchoolId()), userCounts.getOrDefault(r.getId(), 0L)))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public RoleResponse getRole(Long id) {
        SysRole role = getVisibleRole(id);
        return RoleConverter.toResponse(role, schoolNameById(role.getSchoolId()), roleMapper.countActiveUsersByRoleId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponse createRole(RoleCreateRequest request) {
        String code = request.getRoleCode().trim();
        rejectBuiltinPrefix(code);
        assertCodeUnique(code, null);
        // ponytail: 校管理员忽略请求里的 schoolId 直接落本校；超管可建平台级（null）或指定学校
        Long schoolId = SecurityUtils.isSuperAdmin() ? request.getSchoolId() : SecurityUtils.currentSchoolId();

        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(request.getDescription());
        role.setSchoolId(schoolId);
        role.setIsBuiltin(0);
        roleMapper.insert(role);
        return RoleConverter.toResponse(role, schoolNameById(schoolId), 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponse updateRole(Long id, RoleUpdateRequest request) {
        SysRole role = getVisibleRole(id);
        assertManageable(role);

        String newCode = request.getRoleCode() == null ? null : request.getRoleCode().trim();
        boolean codeChanged = StringUtils.hasText(newCode) && !newCode.equals(role.getRoleCode());
        if (codeChanged) {
            if (RoleConverter.isBuiltin(role)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "内置角色的编码不可修改");
            }
            rejectBuiltinPrefix(newCode);
            assertCodeUnique(newCode, id);
        }

        SysRole updated = new SysRole();
        updated.setId(id);
        updated.setRoleCode(codeChanged ? newCode : null);
        updated.setRoleName(StringUtils.hasText(request.getRoleName()) ? request.getRoleName().trim() : null);
        updated.setDescription(request.getDescription());
        roleMapper.updateById(updated);
        return getRole(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = getVisibleRole(id);
        assertManageable(role);
        if (RoleConverter.isBuiltin(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内置角色不可删除");
        }
        long users = roleMapper.countActiveUsersByRoleId(id);
        if (users > 0) {
            throw new BusinessException(ResultCode.DATA_IN_USE, "该角色仍绑定 " + users + " 个用户，请先解除绑定");
        }
        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Override
    public List<PermissionTreeNode> getRolePermissions(Long roleId) {
        getVisibleRole(roleId);
        return permissionMapper.selectByRoleId(roleId).stream()
                .map(RoleConverter::toNode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, RolePermissionsRequest request) {
        SysRole role = getVisibleRole(roleId);
        assertManageable(role);

        Set<Long> requested = request.getPermissionIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysPermission> requestedPerms = requested.isEmpty()
                ? Collections.emptyList()
                : permissionMapper.selectBatchIds(requested);
        if (requestedPerms.size() != requested.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "存在无效的权限ID");
        }

        Set<Long> target = SecurityUtils.isSuperAdmin()
                ? requested
                : resolveTargetForSchoolAdmin(roleId, requestedPerms);

        rolePermissionMapper.deleteByRoleId(roleId);
        if (!target.isEmpty()) {
            rolePermissionMapper.insertBatch(roleId, target);
        }
    }

    /**
     * 校管理员的权限分配规则：
     * <ul>
     *   <li>只能勾选/取消自己持有的权限点（authorities 里的 permission_code）；</li>
     *   <li>角色上已有、但自己未持有的权限点「冻结」原样保留——既不能借全量替换撤掉，也不算越权；</li>
     *   <li>请求里出现自己未持有且角色原本没有的权限点 → 越权，FORBIDDEN。</li>
     * </ul>
     * ponytail: 若简单地要求「请求集合 ⊆ 持有集合」，超管给校级角色加过的高级权限会让校管理员连该角色的
     * 普通权限都改不了（必须把冻结项也传回来，而传回来又算越权）。冻结语义让前端把未持有的节点置灰即可。
     */
    private Set<Long> resolveTargetForSchoolAdmin(Long roleId, List<SysPermission> requestedPerms) {
        Set<String> held = SecurityUtils.currentUser().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        List<SysPermission> existing = permissionMapper.selectByRoleId(roleId);
        Set<Long> existingIds = existing.stream().map(SysPermission::getId).collect(Collectors.toSet());

        List<String> escalations = requestedPerms.stream()
                .filter(p -> !held.contains(p.getPermissionCode()) && !existingIds.contains(p.getId()))
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toList());
        if (!escalations.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能分配自己拥有的权限点：" + String.join(", ", escalations));
        }

        Set<Long> target = new LinkedHashSet<>();
        requestedPerms.stream()
                .filter(p -> held.contains(p.getPermissionCode()))
                .map(SysPermission::getId)
                .forEach(target::add);
        existing.stream()
                .filter(p -> !held.contains(p.getPermissionCode()))
                .map(SysPermission::getId)
                .forEach(target::add);
        return target;
    }

    // ------------------------------------------------------------------
    // 可见性 / 可管理性
    // ------------------------------------------------------------------

    /**
     * 超管可见全部；校管理员可见平台级 + 本校；越界当作不存在
     */
    private SysRole getVisibleRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        if (!SecurityUtils.isSuperAdmin() && role.getSchoolId() != null
                && !role.getSchoolId().equals(SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    /**
     * 超管可改一切；校管理员只能改本校自建角色（内置角色都是平台级，自然被排除）
     */
    private void assertManageable(SysRole role) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        if (role.getSchoolId() == null || !role.getSchoolId().equals(SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能管理本校自建角色");
        }
    }

    /**
     * ponytail: ROLE_ 前缀保留给内置角色——role_code 原样进入 authorities，自定义 ROLE_XXX 会命中 hasRole('XXX')
     */
    private void rejectBuiltinPrefix(String code) {
        if (code.toUpperCase(Locale.ROOT).startsWith(BUILTIN_PREFIX)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "ROLE_ 前缀保留给内置角色，请换一个角色编码");
        }
    }

    private void assertCodeUnique(String code, Long excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "角色编码已存在：" + code);
        }
    }

    // ------------------------------------------------------------------
    // 关联数据补全
    // ------------------------------------------------------------------

    private Map<Long, Long> userCountsByRoleIds(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return roleMapper.countActiveUsersByRoleIds(roleIds).stream()
                .collect(Collectors.toMap(RoleUserCount::getRoleId, RoleUserCount::getUserCount));
    }

    private String schoolNameById(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School school = schoolMapper.selectById(schoolId);
        return school == null ? null : school.getSchoolName();
    }

    private Map<Long, String> schoolNamesByIds(Set<Long> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return schoolMapper.selectBatchIds(schoolIds).stream()
                .collect(Collectors.toMap(School::getId, School::getSchoolName, (a, b) -> a));
    }

}
