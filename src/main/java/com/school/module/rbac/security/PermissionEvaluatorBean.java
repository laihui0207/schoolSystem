package com.school.module.rbac.security;

import com.school.security.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 权限点鉴权 Bean（契约 P）
 * <p>
 * 用法：{@code @PreAuthorize("@perm.has('sys:role:edit')")} / {@code @perm.hasAny('a','b')}。
 * 权限码来自 UserDetailsServiceImpl 装进 authorities 的 sys_permission.permission_code，
 * JWT 过滤器每次请求都会重新加载，所以在角色管理页改了权限立即生效，无需重新登录。
 * <p>
 * ponytail: 超管恒为 true——超管是平台维护者，不应被自己误删的权限绑定锁在外面；
 * 其余身份严格按 authorities 判定，未认证/匿名一律 false 而不抛异常，交给方法安全拦截器返回 403。
 *
 * @author School System
 */
@Component("perm")
public class PermissionEvaluatorBean {

    /**
     * 当前用户是否持有指定权限码
     */
    public boolean has(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser && loginUser.isSuperAdmin()) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }

    /**
     * 持有任一权限码即通过
     */
    public boolean hasAny(String... codes) {
        if (codes == null || codes.length == 0) {
            return false;
        }
        return Arrays.stream(codes).anyMatch(this::has);
    }

}
