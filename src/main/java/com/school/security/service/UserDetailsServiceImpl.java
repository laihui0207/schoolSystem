package com.school.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户详情服务实现
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, CommonConstants.NOT_DELETED)
        );

        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查用户状态
        if (CommonConstants.STATUS_DISABLED.equals(user.getStatus())) {
            log.warn("用户已被禁用: {}", username);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 查询用户角色和权限
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissionCodes = userMapper.selectPermissionCodesByUserId(user.getId());

        // 合并角色和权限
        List<String> authorities = new ArrayList<>();
        authorities.addAll(roleCodes);
        authorities.addAll(permissionCodes);

        // 转换为GrantedAuthority
        List<GrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(grantedAuthorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

}
