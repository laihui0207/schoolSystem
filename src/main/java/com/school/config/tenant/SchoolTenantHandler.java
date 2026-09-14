package com.school.config.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.school.security.util.SecurityUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 学校租户处理器
 * <p>
 * 为所有带 school_id 的表自动追加租户条件，取值来自当前登录身份而非请求参数。
 * 原先各 Mapper 用 {@code <if test="request.schoolId != null">} 拼条件，
 * 客户端不传即查全库、传别校 ID 即读别校数据。
 * <p>
 * 白名单只收 school_id 非空的业务表。{@code sys_user} / {@code sys_role} 的
 * school_id 可为空（平台级角色、超级管理员），且登录鉴权发生在租户上下文建立之前，
 * 一并拦截会在认证阶段把平台角色过滤掉。这两张表由业务层显式控制可见范围。
 *
 * @author School System
 */
@Component
public class SchoolTenantHandler implements TenantLineHandler {

    /**
     * 租户字段名
     */
    private static final String TENANT_COLUMN = "school_id";

    /**
     * 需要自动追加租户条件的表（school_id NOT NULL）
     */
    private static final Set<String> TENANT_TABLES = Set.of(
            "academic_term",
            "grade",
            "class",
            "student",
            "teacher",
            "subject",
            "exam",
            "score"
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(SecurityUtils.currentSchoolId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_COLUMN;
    }

    /**
     * 判断是否跳过租户条件
     * <p>
     * 非租户表直接跳过；超级管理员跨校运维时跳过；未认证场景（登录接口、
     * Flyway 迁移）也跳过，此时无身份可用，放行的是白名单接口自身的查询。
     *
     * @param tableName 表名
     * @return true 表示不追加租户条件
     */
    @Override
    public boolean ignoreTable(String tableName) {
        if (!TENANT_TABLES.contains(tableName.toLowerCase())) {
            return true;
        }
        if (!SecurityUtils.isAuthenticated()) {
            return true;
        }
        return SecurityUtils.isSuperAdmin();
    }

}
