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
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号无学校归属，无法访问校内数据");
        }
        return schoolId;
    }

    /**
     * 查询用的学校ID：超级管理员返回 null，表示不限定学校
     * <p>
     * 手写 XML 不受租户插件改写，schoolId 必须由调用方传入。超级管理员没有
     * 学校归属，直接调 {@link #currentSchoolId()} 会抛异常，跨校查询根本走不到 SQL。
     * <p>
     * 返回 null 即放开全库，因此 null 只允许由这里的超管分支产生：
     * 不要把请求参数直接传给接受此值的 Mapper。
     */
    public static Long currentSchoolIdForQuery() {
        return isSuperAdmin() ? null : currentSchoolId();
    }

    /**
     * 当前用户关联的教师ID
     *
     * @throws BusinessException 未认证，或账号未关联教师档案
     */
    public static Long currentTeacherId() {
        Long teacherId = currentUser().getTeacherId();
        if (teacherId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号未关联教师档案");
        }
        return teacherId;
    }

    /**
     * 当前用户关联的学生ID
     *
     * @throws BusinessException 未认证，或账号未关联学生档案
     */
    public static Long currentStudentId() {
        Long studentId = currentUser().getStudentId();
        if (studentId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号未关联学生档案");
        }
        return studentId;
    }

    /**
     * 是否超级管理员（可跨学校访问）
     */
    public static boolean isSuperAdmin() {
        return currentUser().isSuperAdmin();
    }

    /**
     * 是否校管理员
     */
    public static boolean isSchoolAdmin() {
        return currentUser().isSchoolAdmin();
    }

    /**
     * 是否教师（任课老师或班主任，查询需收窄到任教班级）
     */
    public static boolean isTeacher() {
        return currentUser().isTeacher();
    }

    /**
     * 是否班主任（可审核成绩、管理班级学生名单）
     */
    public static boolean isHeadTeacher() {
        return currentUser().isHeadTeacher();
    }

    /**
     * 是否学生（只能查询自身成绩）
     */
    public static boolean isStudent() {
        return currentUser().isStudent();
    }

    /**
     * 解析写入操作的目标学校ID
     * <p>
     * 租户插件只改写查询条件，INSERT 若已带 school_id 列则不覆盖，
     * 因此新建数据的归属必须在此显式收敛，否则客户端可为别校写入数据。
     * <p>
     * 请求携带的学校ID与当前身份不符时抛异常而非静默改写：
     * 这种请求要么是前端传错，要么是越权尝试，两者都该暴露出来。
     *
     * @param requested 请求携带的学校ID，普通用户可为 null
     * @return 实际写入的学校ID
     * @throws BusinessException 越权指定别校，或超级管理员未指定学校
     */
    public static Long resolveSchoolId(Long requested) {
        if (isSuperAdmin()) {
            if (requested == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "超级管理员操作需指定学校ID");
            }
            return requested;
        }
        Long current = currentSchoolId();
        if (requested != null && !requested.equals(current)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不允许操作其他学校的数据");
        }
        return current;
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
