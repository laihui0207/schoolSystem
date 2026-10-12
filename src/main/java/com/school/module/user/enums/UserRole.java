package com.school.module.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用户角色枚举
 * <p>
 * 统一维护三处取值的映射：前端 role 字段 / sys_user.user_type / sys_role.role_code
 *
 * @author School System
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    SUPER_ADMIN("super_admin", 1, "ROLE_SUPER_ADMIN"),
    /** 校管理员：校级数据隔离，可审核成绩 */
    ADMIN("admin", 2, "ROLE_SCHOOL_ADMIN"),
    /** 教师：userType=3 统一；班主任/任课老师由 LoginUser.isHeadTeacher() 区分 */
    TEACHER("teacher", 3, "ROLE_TEACHER"),
    /** 学生：只能查询自身成绩 */
    STUDENT("student", 4, "ROLE_STUDENT"),
    /** 家长：暂未实现 */
    PARENT("parent", 5, "ROLE_PARENT");

    /**
     * 前端 role 字段值
     */
    private final String code;

    /**
     * 用户类型（sys_user.user_type）
     */
    private final Integer userType;

    /**
     * 角色编码（sys_role.role_code，同时也是 Spring Security 的 authority）
     */
    private final String roleCode;

    /**
     * 根据前端 role 字段值解析
     *
     * @param code 前端 role 字段值
     * @return 角色枚举，未匹配返回 null
     */
    public static UserRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据用户类型解析
     *
     * @param userType 用户类型
     * @return 角色枚举，未匹配返回 null
     */
    public static UserRole fromUserType(Integer userType) {
        return Arrays.stream(values())
                .filter(role -> role.userType.equals(userType))
                .findFirst()
                .orElse(null);
    }

}
