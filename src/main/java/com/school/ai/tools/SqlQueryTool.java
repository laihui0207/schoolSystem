package com.school.ai.tools;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.config.tenant.SchoolTenantHandler;
import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 直接查询数据库工具
 * <p>
 * 安全约束：
 * 1. 仅超级管理员/学校管理员可调用（教师/学生只能用内置工具）
 * 2. 先剥离 -- 行注释与块注释、首尾空白与末尾分号，再做全部检查；残余分号视为多语句直接拒绝
 * 3. 只允许 SELECT 开头；关键词黑名单覆盖写操作、危险函数、系统目录与密码列
 * 4. 用 MyBatis-Plus 租户拦截器对 AST 注入 school_id = 本校（超管不注入）；非超管只能查白名单表，sys_user 必须 JOIN 业务表
 * 5. 命中 sys_user 时拒绝 * / 别名.* / to_json 等整行投影，防止口令哈希外泄
 * 6. 最多返回 200 行，防止大结果集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueryTool {

    private final JdbcTemplate jdbcTemplate;
    private final AiPermissionChecker permissionChecker;
    // ponytail: D-ai-2 注入租户处理器，用 MyBatis-Plus 自己的 TenantLineInnerInterceptor 对 AI SQL 做与 ORM 一致的 school_id 注入
    private final SchoolTenantHandler schoolTenantHandler;

    // ponytail: 如实说明——这不是语法白名单，只是"剥离注释后必须以 SELECT 开头 + 关键词黑名单"的组合校验；
    // 黑名单挡不住未知写法，真正的兜底是 assertIsAdmin 限制调用方、执行与校验同一段剥离后的文本、以及 LIMIT 截断
    private static final Pattern SELECT_ONLY = Pattern.compile(
            "^SELECT\\s+", Pattern.CASE_INSENSITIVE);

    // ponytail: 4c 黑名单补充——写操作、SELECT INTO 建表、休眠/杀会话/大对象/dblink/配置函数、系统目录与统计视图、口令表
    // ponytail: D-ai-1/2 再补——query_to_xml/table_to_xml 等把 SQL/表名当字符串执行，AST 检查看不到其中的 * 与表名；
    // lo_* 读大对象；pg_* 系统目录/配置视图整体禁止（业务表与列没有 pg_ 前缀）
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|COPY|INTO"
            + "|pg_read_file|pg_read_binary_file|pg_ls_\\w*|pg_sleep|pg_terminate_backend|pg_cancel_backend"
            + "|pg_reload_conf|lo_import|lo_export|dblink\\w*|set_config|current_setting"
            + "|information_schema|pg_catalog|pg_stat\\w*|pg_shadow|pg_authid|pg_roles|pg_user"
            + "|\\w*_to_xml\\w*|lo_\\w*|pg_\\w*"
            + "|\\\\!)\\b",
            Pattern.CASE_INSENSITIVE);

    // 同时出现 sys_user 与 password 即拒绝，禁止读取口令哈希
    private static final Pattern SYS_USER = Pattern.compile("\\bsys_user\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD = Pattern.compile("\\bpassword\\b", Pattern.CASE_INSENSITIVE);

    // 反斜杠转义串 E'\'' 与 $$ 美元引用会让注释剥离器对"字符串在哪结束"的判断和 PG 不一致，整体拒绝
    private static final Pattern UNSUPPORTED_LEXEMES = Pattern.compile("[\\\\$]");

    private static final int MAX_ROWS = 200;

    // ponytail: D-ai-2 与 com.school.config.tenant.SchoolTenantHandler.TENANT_TABLES 保持一致（school_id NOT NULL 的业务表，
    // 租户插件会为它们注入 school_id）；那边增删表时这里必须同步，否则新表要么被白名单拒绝、要么没被注入却被放行
    private static final Set<String> TENANT_TABLES = Set.of(
            "academic_term", "grade", "class", "student", "teacher", "subject", "exam", "score");

    // 无 school_id 的纯关联表：只含 ID，允许非超管与业务表一起查
    private static final Set<String> LINK_TABLES = Set.of(
            "teacher_class", "class_student", "exam_subject", "parent_student");

    private static final String SYS_USER_TABLE = "sys_user";

    private static final String ALLOWED_TABLE_HINT = String.join(", ", new TreeSet<>(TENANT_TABLES))
            + ", " + String.join(", ", new TreeSet<>(LINK_TABLES))
            + ", " + SYS_USER_TABLE + "（须 JOIN 业务表）";

    // ponytail: D-ai-1 整行投影函数——把 sys_user 一整行（含 password）转成 JSON/hstore 返回
    private static final Set<String> ROW_PROJECTION_FUNCTIONS = Set.of(
            "row_to_json", "to_json", "to_jsonb", "json_agg", "jsonb_agg", "hstore");

    @Tool(description = """
            直接执行 SQL 查询数据库。仅管理员角色（超级管理员/学校管理员）可用；仅支持单条 SELECT，最多返回 200 行。

            当内置工具返回字段不足或无法满足需求时，管理员可调用此工具补查。

            适用场景：
            - 查询教师/学生的联系电话：SELECT t.name, u.phone FROM teacher t JOIN sys_user u ON u.id = t.user_id WHERE t.name LIKE '%姓名%' AND t.deleted=0
            - 查询班主任联系方式
            - 查询家长联系方式：SELECT u.real_name, u.phone FROM parent_student ps JOIN sys_user u ON u.id = ps.parent_user_id JOIN student s ON s.id = ps.student_id WHERE s.id=X AND s.deleted=0
            - 人数统计、聚合查询等内置工具未覆盖的场景

            使用规则：
            1. 租户隔离由系统自动完成：非超级管理员的查询会被自动为 teacher/student/class/grade/subject/exam/score/academic_term 的每个引用追加 school_id = 当前学校 条件，无需手写
            2. 非超级管理员只能查询这些表：academic_term, grade, class, student, teacher, subject, exam, score, teacher_class, class_student, exam_subject, parent_student, sys_user；其他表（含系统目录）会被拒绝
            3. sys_user 必须与上述业务表 JOIN 使用（如 JOIN teacher / student），且必须明确列出字段：禁止 SELECT * / u.* / to_json(u) 等整行投影，禁止查询 password
            4. 只写一条 SELECT：不要加注释、不要以分号结尾、不要拼接多条语句；SQL 必须能被标准 SQL 解析器解析
            5. 所有业务表查询必须加 deleted = 0（sys_user 除外）
            6. teacher 表无 phone 字段，电话在 sys_user 表，需 JOIN sys_user ON sys_user.id = teacher.user_id
            7. 时间字段使用 PostgreSQL 语法，如 created_at::date
            """)
    public List<Map<String, Object>> executeSql(
            @ToolParam(description = "要执行的单条 SELECT SQL 语句（不含注释与分号）") String sql,
            ToolContext toolContext) {

        // ponytail: 4a 调用方限制——executeSql 绕过 MyBatis 租户插件直连 JDBC，只允许超管/校管理员使用
        permissionChecker.assertIsAdmin(toolContext);
        LoginUser user = permissionChecker.currentUser(toolContext);

        if (sql == null || sql.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "SQL 不能为空");
        }
        if (UNSUPPORTED_LEXEMES.matcher(sql).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "SQL 不允许包含反斜杠或 $ 符号");
        }

        // ponytail: 4b 先剥离注释/首尾空白/末尾分号再检查，并且执行的也是剥离后的文本，保证"检查的"和"执行的"是同一段 SQL
        String normalized = normalize(sql);
        if (normalized.indexOf(';') >= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不允许执行多条语句");
        }
        if (!SELECT_ONLY.matcher(normalized).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只允许执行 SELECT 语句");
        }
        if (DANGEROUS.matcher(normalized).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "SQL 包含不允许的关键词");
        }
        // ponytail: 4c 禁止查 sys_user.password——同时出现 sys_user 与 password 即拒绝，不做列级解析
        if (SYS_USER.matcher(normalized).find() && PASSWORD.matcher(normalized).find()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "禁止查询用户密码字段");
        }

        boolean superAdmin = user.isSuperAdmin();
        if (!superAdmin && user.getSchoolId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号无学校归属");
        }

        // ponytail: D-ai-2 不再用正则判定 school_id = X（NOT / IS NOT TRUE / 子查询 / SELECT 列表都能骗过正则），
        // 改为用 MyBatis-Plus 租户拦截器对 AST 注入：每个租户表引用（含 JOIN / 子查询 / 派生表 / UNION 分支）都追加 school_id = 当前校；
        // 超管因 SchoolTenantHandler.ignoreTable 返回 true 不改写。后面校验与执行的都是改写后的 SQL，而不是模型给的原文
        String tenantSql = injectTenantCondition(normalized);
        Statement statement = parseSelect(tenantSql);

        SqlInspector inspector = new SqlInspector();
        Set<String> tables = inspector.getTables(statement).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!superAdmin) {
            assertAllowedTables(tables);
        }
        if (tables.contains(SYS_USER_TABLE)) {
            assertNoRowProjection(inspector);
        }

        log.info("AI SQL query: userId={}, schoolId={}, sql={}",
                user.getUserId(), user.getSchoolId(), tenantSql);

        // 加 LIMIT 保护
        String safeSql = wrapLimit(tenantSql);

        try {
            return jdbcTemplate.queryForList(safeSql);
        } catch (Exception e) {
            log.warn("AI SQL query failed: {}", e.getMessage());
            throw new BusinessException("SQL 执行失败：" + e.getMessage());
        }
    }

    /**
     * 用 TenantLineInnerInterceptor 对 SQL 的每个租户表引用注入 school_id = 当前校（与 ORM 走的是同一段逻辑）。
     * 解析失败（parserSingle 把 JSQLParserException 包成 MybatisPlusException）直接拒绝。
     */
    private String injectTenantCondition(String sql) {
        try {
            return new TenantLineInnerInterceptor(schoolTenantHandler).parserSingle(sql, null);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI SQL tenant rewrite failed: {}", e.getMessage());
            throw new BusinessException(ResultCode.FORBIDDEN, "SQL 无法解析，已拒绝执行，请检查语法后重试");
        }
    }

    /**
     * 解析改写后的 SQL 供表名/投影检查；非 SELECT 语句拒绝
     */
    private Statement parseSelect(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            log.warn("AI SQL parse failed: {}", e.getMessage());
            throw new BusinessException(ResultCode.FORBIDDEN, "SQL 无法解析，已拒绝执行，请检查语法后重试");
        }
        if (!(statement instanceof Select)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只允许执行 SELECT 语句");
        }
        return statement;
    }

    /**
     * 非超管：只能查租户表 ∪ 关联表 ∪ sys_user；sys_user 必须与至少一个租户表同句出现（JOIN），
     * 否则没有任何 school_id 条件可注入，等于全平台账号表裸查
     */
    private void assertAllowedTables(Set<String> tables) {
        List<String> notAllowed = tables.stream()
                .filter(t -> !TENANT_TABLES.contains(t) && !LINK_TABLES.contains(t) && !SYS_USER_TABLE.equals(t))
                .collect(Collectors.toList());
        if (!notAllowed.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "非超级管理员不允许查询表 " + notAllowed + "，仅可查询：" + ALLOWED_TABLE_HINT);
        }
        if (tables.contains(SYS_USER_TABLE) && Collections.disjoint(tables, TENANT_TABLES)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "查询 sys_user 时必须与 teacher / student 等带 school_id 的业务表 JOIN，不能单独查询");
        }
    }

    // ponytail: D-ai-1 命中 sys_user 时拒绝整行投影：SELECT * / 别名.* / to_json(别名) / 裸别名（u、u::text、array_agg(u)）都会把 password 列带出来；
    // 用 JSqlParser 遍历 AST 判定，比正则稳，且覆盖子查询/派生表里的投影
    private void assertNoRowProjection(SqlInspector inspector) {
        List<String> violations = inspector.rowProjections();
        if (!violations.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "查询 sys_user 时禁止整行投影（" + String.join("、", violations)
                    + "），请明确列出需要的字段，如 u.real_name, u.phone");
        }
    }

    /**
     * 在 TablesNamesFinder 的遍历基础上顺带收集：表名与别名、裸列引用、整行投影节点。
     * 先调用 getTables(statement) 完成遍历，之后再读取收集结果。
     */
    private static final class SqlInspector extends TablesNamesFinder<Void> {

        private final Set<String> tableNamesAndAliases = new HashSet<>();
        private final List<Column> bareColumns = new ArrayList<>();
        private final List<String> projections = new ArrayList<>();

        @Override
        public <S> Void visit(Table table, S context) {
            if (table.getName() != null) {
                tableNamesAndAliases.add(table.getName().toLowerCase(Locale.ROOT));
            }
            if (table.getAlias() != null && table.getAlias().getName() != null) {
                tableNamesAndAliases.add(table.getAlias().getName().toLowerCase(Locale.ROOT));
            }
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(Column column, S context) {
            Table table = column.getTable();
            if (table == null || table.getName() == null || table.getName().isEmpty()) {
                bareColumns.add(column);
            }
            return super.visit(column, context);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            if (ROW_PROJECTION_FUNCTIONS.contains(simpleName(function.getName()))) {
                projections.add(function.getName() + "(...)");
            }
            if (isCountAll(function)) {
                // count(*) 不投影任何列，不下钻去访问它参数里的 *
                return null;
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(AllColumns allColumns, S context) {
            projections.add("*");
            return super.visit(allColumns, context);
        }

        @Override
        public <S> Void visit(AllTableColumns allTableColumns, S context) {
            projections.add(allTableColumns.toString());
            return super.visit(allTableColumns, context);
        }

        /**
         * 整行投影：* / 别名.* / 整行函数 / 与表名或别名同名的裸列（SELECT u、u::text、array_agg(u) 这类复合行引用）
         */
        List<String> rowProjections() {
            List<String> result = new ArrayList<>(projections);
            for (Column column : bareColumns) {
                String name = column.getColumnName() == null ? "" : column.getColumnName().toLowerCase(Locale.ROOT);
                if (tableNamesAndAliases.contains(name)) {
                    result.add("裸表引用 " + column.getColumnName());
                }
            }
            return result;
        }

        private static boolean isCountAll(Function function) {
            ExpressionList<?> params = function.getParameters();
            return "count".equals(simpleName(function.getName()))
                    && params != null && params.size() == 1
                    && params.get(0) instanceof AllColumns
                    && !(params.get(0) instanceof AllTableColumns);
        }

        private static String simpleName(String functionName) {
            if (functionName == null) {
                return "";
            }
            String lower = functionName.toLowerCase(Locale.ROOT);
            return lower.substring(lower.lastIndexOf('.') + 1);
        }
    }

    /**
     * 剥离注释后去掉首尾空白与末尾分号
     */
    static String normalize(String sql) {
        return stripComments(sql).strip().replaceAll("[;\\s]+$", "");
    }

    /**
     * 剥离 -- 行注释与块注释（PG 块注释可嵌套），单引号字符串与双引号标识符内的内容原样保留。
     * 校验与执行都使用剥离后的 SQL，保证"检查的"和"执行的"是同一段文本。
     */
    static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                int close = findQuoteEnd(sql, i, c);
                out.append(sql, i, close);
                i = close;
            } else if (sql.startsWith("--", i)) {
                int eol = sql.indexOf('\n', i);
                // 保留换行符本身作为分隔
                i = eol < 0 ? n : eol;
            } else if (sql.startsWith("/*", i)) {
                i = skipBlockComment(sql, i);
                // 注释位置补一个空格，避免 a/*x*/b 粘连成 ab
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 返回引号串结束位置之后的下标（'' / "" 为转义），未闭合则到文本末尾
     */
    private static int findQuoteEnd(String sql, int open, char quote) {
        int n = sql.length();
        int i = open + 1;
        while (i < n) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < n && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /**
     * 跳过从 open 开始的块注释（支持嵌套），返回注释结束之后的下标
     */
    private static int skipBlockComment(String sql, int open) {
        int n = sql.length();
        int depth = 1;
        int i = open + 2;
        while (i < n && depth > 0) {
            if (sql.startsWith("/*", i)) {
                depth++;
                i += 2;
            } else if (sql.startsWith("*/", i)) {
                depth--;
                i += 2;
            } else {
                i++;
            }
        }
        return i;
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
