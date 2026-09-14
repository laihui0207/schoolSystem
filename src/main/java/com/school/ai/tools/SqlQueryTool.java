package com.school.ai.tools;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 直接查询数据库工具
 * <p>
 * 安全约束：
 * 1. 只允许 SELECT 语句
 * 2. 非超管自动追加 school_id 条件（由模型在 SQL 中包含，此处校验）
 * 3. 最多返回 200 行，防止大结果集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueryTool {

    private final JdbcTemplate jdbcTemplate;
    private final AiPermissionChecker permissionChecker;

    // ponytail: 白名单比黑名单更安全；只允许 SELECT 开头（忽略前导空白和注释）
    private static final Pattern SELECT_ONLY = Pattern.compile(
            "^\\s*(--[^\\n]*\\n\\s*)*SELECT\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // 禁止的关键词：写操作、函数调用、系统表访问
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE" +
            "|pg_read_file|pg_ls_dir|COPY|\\\\!)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_ROWS = 200;

    @Tool(description = """
            当其他工具无法满足需求时，直接执行 SQL 查询数据库。
            仅支持 SELECT 语句。最多返回 200 行。

            使用规则：
            1. 非超级管理员必须在 WHERE 中包含 school_id = <当前用户schoolId> 条件，确保数据隔离
            2. 表名参考数据库文档：score, student, teacher, class, exam, subject, grade,
               academic_term, school, teacher_class, class_student, score_change_log 等
            3. 软删除字段为 deleted=0，查询时必须加上
            4. 时间字段使用 PostgreSQL 语法，如 created_at::date
            5. 如结果超过 200 行会被截断，请用 WHERE/LIMIT 缩小范围
            """)
    public List<Map<String, Object>> executeSql(
            @ToolParam(description = "要执行的 SELECT SQL 语句") String sql) {

        LoginUser user = permissionChecker.currentUser();

        // 安全检查
        if (!SELECT_ONLY.matcher(sql).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只允许执行 SELECT 语句");
        }
        if (DANGEROUS.matcher(sql).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "SQL 包含不允许的关键词");
        }

        // 非超管：校验 SQL 中包含 school_id 条件，防止跨校查询
        if (!user.isSuperAdmin()) {
            Long schoolId = user.getSchoolId();
            if (schoolId == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前账号无学校归属");
            }
            String sqlLower = sql.toLowerCase();
            // ponytail: 简单字符串检查，模型生成的 SQL 一定要带 school_id 条件
            if (!sqlLower.contains("school_id")) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "查询必须包含 school_id 条件（当前 schoolId=" + schoolId + "）");
            }
        }

        log.info("AI SQL query: userId={}, schoolId={}, sql={}",
                user.getUserId(), user.getSchoolId(), sql);

        // 加 LIMIT 保护
        String safeSql = wrapLimit(sql);

        try {
            return jdbcTemplate.queryForList(safeSql);
        } catch (Exception e) {
            log.warn("AI SQL query failed: {}", e.getMessage());
            throw new BusinessException("SQL 执行失败：" + e.getMessage());
        }
    }

    /**
     * 若 SQL 未包含 LIMIT，追加 LIMIT MAX_ROWS；
     * 若已有 LIMIT 且超过 MAX_ROWS，替换为 MAX_ROWS。
     */
    private String wrapLimit(String sql) {
        String trimmed = sql.stripTrailing().replaceAll(";\\s*$", "");
        Pattern limitPat = Pattern.compile("\\bLIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        var matcher = limitPat.matcher(trimmed);
        if (matcher.find()) {
            int existing = Integer.parseInt(matcher.group(1));
            if (existing > MAX_ROWS) {
                return trimmed.substring(0, matcher.start(1)) + MAX_ROWS
                        + trimmed.substring(matcher.end(1));
            }
            return trimmed;
        }
        return trimmed + " LIMIT " + MAX_ROWS;
    }
}
