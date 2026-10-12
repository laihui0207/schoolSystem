package com.school.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.parent.mapper.ParentStudentMapper;
import com.school.module.teacher.mapper.TeacherClassMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户详情服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final ParentStudentMapper parentStudentMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, CommonConstants.NOT_DELETED)
        );

        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (CommonConstants.STATUS_DISABLED.equals(user.getStatus())) {
            log.warn("用户已被禁用: {}", username);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        List<String> roleCodes = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissionCodes = userMapper.selectPermissionCodesByUserId(user.getId());

        List<GrantedAuthority> authorities = roleCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        // 教师账号额外解析 teacher.id，数据隔离按任教班级收窄时需要
        boolean isTeacher = UserRole.TEACHER.getUserType().equals(user.getUserType());
        Long teacherId = isTeacher ? userMapper.selectTeacherIdByUserId(user.getId()) : null;

        // 班主任判断：该教师是否担任任一班级的班主任
        boolean headTeacher = isTeacher && teacherId != null
                && !teacherClassMapper.selectHeadClassIdsByTeacherId(teacherId).isEmpty();

        // 学生账号额外解析 student.id，用于校验只能访问自身趋势数据
        Long studentId = UserRole.STUDENT.getUserType().equals(user.getUserType())
                ? userMapper.selectStudentIdByUserId(user.getId())
                : null;

        // ponytail: 契约 L——家长账号装载已绑定孩子的 student.id；JwtAuthenticationFilter 每次请求都重新 load，
        // 管理员绑定/解绑后无需重新登录即生效
        List<Long> childStudentIds = UserRole.PARENT.getUserType().equals(user.getUserType())
                ? parentStudentMapper.selectStudentIdsByParentUserId(user.getId())
                : List.of();

        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .schoolId(user.getSchoolId())
                .userType(user.getUserType())
                .teacherId(teacherId)
                .studentId(studentId)
                .headTeacher(headTeacher)
                .childStudentIds(childStudentIds)
                .authorities(authorities)
                .build();
    }

}
