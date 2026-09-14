package com.school.security.util;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具
 * <p>
 * 数据隔离的取值入口。所有方法在无法解析身份时抛异常而非返回 null——
 * 返回 null 会让调用方的租户条件静默消失，那正是隔离失效的成因。
 *
 * @author School System
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 当前登录用户
     *
     * @throws BusinessException 未认证，或主体不是 LoginUser（如匿名访问）
     */
    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 当前用户ID
     */
    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    /**
     * 当前用户所属学校ID
     * <p>
     * 超级管理员无归属学校，调用前应先用 {@link #isSuperAdmin()} 分流；
     * 直接调用会抛异常，而不是返回 null 放行全库。
     *
     * @throws BusinessException 未认证，或账号无学校归属
     */
    public static Long currentSchoolId() {
        Long schoolId = currentUser().getSchoolId();
        if (schoolId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "当前账号无学校归属，无法访问校内数据");
        }
        return schoolId;
    }

    /**
     * 当前用户关联的教师ID
     *
     * @throws BusinessException 未认证，或账号未关联教师档案
     */
    public static Long currentTeacherId() {
        Long teacherId = currentUser().getTeacherId();
        if (teacherId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "当前账号未关联教师档案");
        }
        return teacherId;
    }

    /**
     * 是否超级管理员（可跨学校访问）
     */
    public static boolean isSuperAdmin() {
        return currentUser().isSuperAdmin();
    }

    /**
     * 是否教师（查询需收窄到任教班级）
     */
    public static boolean isTeacher() {
        return currentUser().isTeacher();
    }

    /**
     * 是否已认证且主体为 LoginUser
     * <p>
     * 供无需强制认证的场景（如可选的租户注入）判断，不抛异常。
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof LoginUser;
    }

}
