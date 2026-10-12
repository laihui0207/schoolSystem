package com.school.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.service.RefreshTokenStore;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserQueryRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.dto.UserUpdateRequest;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final SchoolMapper schoolMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUser(UserCreateRequest request) {
        UserRole role = resolveRole(request.getRole());
        String phone = normalizePhone(request.getPhone());
        Integer status = request.getStatus() != null ? request.getStatus() : CommonConstants.STATUS_ENABLED;
        validateStatus(status);

        // 校验用户名是否已存在（数据库唯一约束由 GlobalExceptionHandler 兜底）
        Long sameUsername = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (sameUsername > 0) {
            throw new BusinessException(ResultCode.USER_EXISTS, "用户名已存在");
        }
        checkPhoneUnique(phone, null);

        // 构建用户实体，密码 BCrypt 加密后入库
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(phone);
        user.setUserType(role.getUserType());
        // sys_user 的 school_id 可空（平台级账号），故不能直接套 resolveSchoolId：
        // 超管可建平台账号或指定学校，其余人只能建本校账号
        Long schoolId = SecurityUtils.isSuperAdmin()
                ? request.getSchoolId()
                : SecurityUtils.resolveSchoolId(request.getSchoolId());
        requireSchoolForRole(role, schoolId);
        user.setSchoolId(schoolId);
        user.setStatus(status);

        sysUserMapper.insert(user);
        bindRole(user.getId(), role);

        return convertToResponse(user, schoolNameById(user.getSchoolId()));
    }

    @Override
    public UserResponse getUserById(Long id) {
        SysUser user = getExistingUser(id);
        // ponytail: sys_user 不在租户插件表清单内，校管理员按自增 id 枚举能读到他校师生乃至平台超管的手机号；
        // 非超管只能看本校用户，越界一律当作不存在，不泄露 id 是否存在
        if (!SecurityUtils.isSuperAdmin() && !Objects.equals(user.getSchoolId(), SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToResponse(user, schoolNameById(user.getSchoolId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        SysUser existing = getExistingUser(id);

        UserRole newRole = request.getRole() != null ? resolveRole(request.getRole()) : null;
        if (request.getStatus() != null) {
            validateStatus(request.getStatus());
            if (CommonConstants.STATUS_DISABLED.equals(request.getStatus())) {
                rejectSelfOperation(existing);
            }
        }

        // phone 为 null 表示不修改；空串表示清空
        boolean phoneProvided = request.getPhone() != null;
        String phone = phoneProvided ? normalizePhone(request.getPhone()) : existing.getPhone();
        if (phone != null && !phone.equals(existing.getPhone())) {
            checkPhoneUnique(phone, id);
        }

        // 构建更新对象（username / password 不在此接口修改）
        SysUser updated = new SysUser();
        updated.setId(id);
        updated.setRealName(request.getRealName() != null ? request.getRealName() : existing.getRealName());
        updated.setPhone(phone);
        updated.setUserType(newRole != null ? newRole.getUserType() : existing.getUserType());
        // 仅超管可改学校归属；校内管理员传了也忽略，避免把用户划到别的学校
        updated.setSchoolId(SecurityUtils.isSuperAdmin() && request.getSchoolId() != null
                ? request.getSchoolId()
                : existing.getSchoolId());
        updated.setStatus(request.getStatus() != null ? request.getStatus() : existing.getStatus());
        // 仅在改角色/改学校时校验，避免历史上已无学校归属的账号连状态都改不了
        UserRole finalRole = newRole != null ? newRole : UserRole.fromUserType(existing.getUserType());
        if (finalRole != null && (newRole != null || request.getSchoolId() != null)) {
            requireSchoolForRole(finalRole, updated.getSchoolId());
        }

        // MyBatis-Plus 更新时跳过 null 字段，清空手机号需显式 SET NULL
        boolean clearPhone = phoneProvided && phone == null && existing.getPhone() != null;
        sysUserMapper.update(updated, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .setSql(clearPhone, "phone = NULL"));

        // 角色变更时同步 sys_user_role（Spring Security authorities 的来源）
        if (newRole != null && !newRole.getUserType().equals(existing.getUserType())) {
            sysUserMapper.deleteUserRolesByUserId(id);
            bindRole(id, newRole);
        }
        if (CommonConstants.STATUS_DISABLED.equals(request.getStatus())) {
            // ponytail: 禁用账号同时作废其 refresh token（契约 T），禁用期间不能再换出新会话
            refreshTokenStore.revokeByUserId(id);
        }

        return getUserById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        validateStatus(status);
        SysUser existing = getExistingUser(id);
        if (CommonConstants.STATUS_DISABLED.equals(status)) {
            rejectSelfOperation(existing);
        }

        SysUser updated = new SysUser();
        updated.setId(id);
        updated.setStatus(status);
        sysUserMapper.updateById(updated);
        if (CommonConstants.STATUS_DISABLED.equals(status)) {
            // ponytail: 禁用账号同时作废其 refresh token（契约 T）；access token 由 UserDetailsServiceImpl 的禁用校验拦截
            refreshTokenStore.revokeByUserId(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        getExistingUser(id);

        SysUser updated = new SysUser();
        updated.setId(id);
        updated.setPassword(passwordEncoder.encode(password));
        sysUserMapper.updateById(updated);
        // ponytail: 重置密码后作废该用户的 refresh token（契约 T），已泄露的刷新令牌不能再续命；
        // access token 仍以 token 串为 key 无法按用户定位，会在 app.jwt.expiration 内自然过期
        refreshTokenStore.revokeByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser existing = getExistingUser(id);
        rejectSelfOperation(existing);

        // 逻辑删除用户（sys_user_role 关联保留，便于恢复）
        sysUserMapper.deleteById(id);
        refreshTokenStore.revokeByUserId(id);
    }

    @Override
    public Page<UserResponse> listUsers(UserQueryRequest request) {
        // ponytail: sys_user 不在租户插件表清单内，跨校隔离只能在这里收口；非超管忽略客户端传入的 schoolId
        if (!SecurityUtils.isSuperAdmin()) {
            request.setSchoolId(SecurityUtils.currentSchoolId());
        }

        UserRole role = StringUtils.hasText(request.getRole()) ? resolveRole(request.getRole()) : null;
        Integer userType = role != null ? role.getUserType() : null;

        // ponytail: 用 Wrapper 代替 XML 分页，不复制 teacher 模块 ORDER BY ${orderBy} 的 SQL 注入点；固定按创建时间倒序
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(request.getUsername()), SysUser::getUsername, request.getUsername())
                .like(StringUtils.hasText(request.getRealName()), SysUser::getRealName, request.getRealName())
                .eq(userType != null, SysUser::getUserType, userType)
                .eq(request.getSchoolId() != null, SysUser::getSchoolId, request.getSchoolId())
                .eq(request.getStatus() != null, SysUser::getStatus, request.getStatus())
                .orderByDesc(SysUser::getCreatedAt);

        // 构建分页对象并查询
        Page<SysUser> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<SysUser> resultPage = sysUserMapper.selectPage(page, wrapper);

        // 批量补全学校名称
        Set<Long> schoolIds = resultPage.getRecords().stream()
                .map(SysUser::getSchoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> schoolNames = schoolNamesByIds(schoolIds);

        // 转换为响应DTO
        Page<UserResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<UserResponse> responses = resultPage.getRecords().stream()
                .map(user -> convertToResponse(user, schoolNames.get(user.getSchoolId())))
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    /**
     * 查询用户，不存在则抛出业务异常
     */
    private SysUser getExistingUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 非超级管理员角色必须有学校归属
     * <p>
     * ponytail: school_id 为空的 admin/teacher/student 登录后任何租户表查询都会在
     * SecurityUtils.currentSchoolId() 抛 FORBIDDEN，是个登录即不可用的死账号；只有超管可建平台级账号。
     * 家长（parent）同样必须有学校归属：绑定孩子时按同校校验，租户插件也靠它收窄 student 查询
     */
    private void requireSchoolForRole(UserRole role, Long schoolId) {
        if (role != UserRole.SUPER_ADMIN && schoolId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非超级管理员角色必须指定学校");
        }
    }

    /**
     * 解析前端 role 字段
     */
    private UserRole resolveRole(String code) {
        UserRole role = UserRole.fromCode(code);
        if (role == null) {
            throw new BusinessException("角色无效，可选值：super_admin / admin / teacher / student / parent");
        }
        return role;
    }

    /**
     * 校验状态值
     */
    private void validateStatus(Integer status) {
        if (!VALID_STATUS.contains(status)) {
            throw new BusinessException("状态值无效，只能是0（禁用）或1（启用）");
        }
    }

    /**
     * 手机号空串归一化为 null：sys_user.phone 有唯一约束，多个空串会互相冲突
     */
    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    /**
     * 校验手机号唯一
     *
     * @param phone 手机号，null 跳过
     * @param excludeId 排除的用户ID（更新时为自身）
     */
    private void checkPhoneUnique(String phone, Long excludeId) {
        if (phone == null) {
            return;
        }
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }
    }

    /**
     * 绑定角色到 sys_user_role
     */
    private void bindRole(Long userId, UserRole role) {
        if (sysUserMapper.insertUserRole(userId, role.getRoleCode()) == 0) {
            throw new BusinessException("角色不存在：" + role.getRoleCode());
        }
    }

    /**
     * 禁止删除/禁用当前登录账号，避免唯一超管把自己锁在系统外
     */
    private void rejectSelfOperation(SysUser target) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && target.getUsername().equals(authentication.getName())) {
            throw new BusinessException("不能对当前登录账号执行此操作");
        }
    }

    /**
     * 查询单个学校名称
     */
    private String schoolNameById(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School school = schoolMapper.selectById(schoolId);
        return school != null ? school.getSchoolName() : null;
    }

    /**
     * 批量查询学校名称
     */
    private Map<Long, String> schoolNamesByIds(Set<Long> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return schoolMapper.selectBatchIds(schoolIds).stream()
                .collect(Collectors.toMap(School::getId, School::getSchoolName));
    }

    /**
     * 转换为响应DTO（不含密码）
     */
    private UserResponse convertToResponse(SysUser user, String schoolName) {
        UserRole role = UserRole.fromUserType(user.getUserType());
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(role != null ? role.getCode() : null)
                .userType(user.getUserType())
                .schoolId(user.getSchoolId())
                .schoolName(schoolName)
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

}
