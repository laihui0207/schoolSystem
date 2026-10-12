# 数据库表结构（PostgreSQL）

所有表均使用软删除字段 `deleted SMALLINT DEFAULT 0`（除 sys_operation_log、score_statistics）。
时间字段 `created_at` / `updated_at` 默认 `CURRENT_TIMESTAMP`。
所有外键为逻辑约束（应用层保证，无 DDL REFERENCES）。

---

## school — 学校（顶层租户）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_code | VARCHAR(50) UNIQUE NOT NULL | 学校编码 |
| school_name | VARCHAR(100) NOT NULL | 学校名称 |
| region_code | VARCHAR(20) | 区域编码；同一编码的学校可跨校对比（/stats/region） |
| address | VARCHAR(255) | 地址 |
| contact_phone | VARCHAR(20) | 联系电话 |
| contact_person | VARCHAR(50) | 联系人 |
| school_type | VARCHAR(20) | PUBLIC/PRIVATE |
| province | VARCHAR(50) | 省 |
| city | VARCHAR(50) | 市 |
| district | VARCHAR(50) | 区 |
| status | SMALLINT DEFAULT 1 | 0=禁用,1=启用 |

---

## sys_user — 系统用户

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| username | VARCHAR(50) UNIQUE NOT NULL | 登录名 |
| password | VARCHAR(200) NOT NULL | BCrypt |
| phone | VARCHAR(20) UNIQUE | |
| real_name | VARCHAR(50) | |
| user_type | SMALLINT NOT NULL | 1=超管,2=校管理员,3=教师,4=学生,5=家长 |
| school_id | BIGINT | → school.id |
| status | SMALLINT DEFAULT 1 | 0=禁用,1=启用 |
| phone_verified | SMALLINT DEFAULT 0 | 手机号已验证（V22）：短信注册/短信登录成功置 1，管理员录入的手机号为 0 |

自助注册（学生/家长）的账号 `username = phone`。

---

## sys_role — 角色

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| role_code | VARCHAR(50) UNIQUE NOT NULL | e.g. ROLE_TEACHER；ROLE_ 前缀保留给内置角色 |
| role_name | VARCHAR(50) NOT NULL | |
| description | VARCHAR(200) | |
| school_id | BIGINT | NULL=平台级角色；校管理员自建角色落本校 |
| is_builtin | SMALLINT DEFAULT 0 | V19：1=内置（ROLE_SUPER_ADMIN/SCHOOL_ADMIN/TEACHER/STUDENT/PARENT），不可删、role_code 不可改 |

## sys_user_role — 用户角色关联
`(user_id, role_id)` UNIQUE。无 deleted 列。user_type 对应的内置角色始终绑定；其余为「附加角色」（PUT /users/{id}/roles），只能是自定义角色。

## sys_permission — 权限
`permission_code` UNIQUE，`resource_type`: 1=菜单,2=按钮,3=API；`parent_id` 组成树（NULL=一级菜单），`url` 为后端路径，`sort_order` 同级排序。
V5 预置 23 条，V19 补齐到约 118 条：一级菜单 `<module>:manage`（sys/school/term/grade/class/subject/student/teacher/exam/score/stat/export/notification/warning），
系统管理下再分 `sys:user/role/permission/dict/config/log/job/backup` 二级，叶子形如 `term:list`、`score:review`、`score:change-approve`、`stat:region`、`export:pdf`、`sys:user:assign-role`。
permission_code 原样进入 Spring Security authorities，`@PreAuthorize("@perm.has('sys:role:edit')")` 按它判定（超管恒 true）。

## sys_role_permission — 角色权限关联
`(role_id, permission_id)` UNIQUE。无 deleted 列，物理删除。V19 按角色默认分配：超管全部；校管理员本校业务 + sys:role:*；教师录入/查询/统计/导出；学生/家长只读自身。

---

## academic_term — 学期

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_id | BIGINT NOT NULL | → school.id |
| year | VARCHAR(20) | e.g. "2025-2026" |
| term | SMALLINT | 1=上学期,2=下学期 |
| start_date | DATE | |
| end_date | DATE | |
| status | SMALLINT DEFAULT 1 | 0=结束,1=进行中 |

---

## grade — 年级

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_id | BIGINT NOT NULL | → school.id |
| grade_name | VARCHAR(50) NOT NULL | e.g. "高一" |
| grade_code | INT NOT NULL | 排序用 |

---

## class — 班级

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_id | BIGINT NOT NULL | → school.id |
| grade_id | BIGINT NOT NULL | → grade.id |
| class_name | VARCHAR(50) NOT NULL | |
| class_code | INT NOT NULL | |
| term_id | BIGINT NOT NULL | → academic_term.id |
| head_teacher_id | BIGINT | → teacher.id（班主任） |

---

## student — 学生

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT UNIQUE | → sys_user.id |
| student_no | VARCHAR(30) UNIQUE NOT NULL | 学号 |
| school_id | BIGINT NOT NULL | → school.id |
| name | VARCHAR(50) NOT NULL | |
| gender | SMALLINT | 1=男,2=女 |
| birth_date | DATE | |
| enrollment_date | DATE | |
| status | SMALLINT DEFAULT 1 | 0=离校,1=在读,2=休学 |

## class_student — 学生班级关联
`(class_id, student_id)` UNIQUE

---

## teacher — 教师

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT UNIQUE | → sys_user.id |
| teacher_no | VARCHAR(30) UNIQUE NOT NULL | 工号 |
| school_id | BIGINT NOT NULL | → school.id |
| name | VARCHAR(50) NOT NULL | |
| gender | SMALLINT | 1=男,2=女 |
| subject_id | BIGINT | → subject.id（主教科目） |
| status | SMALLINT DEFAULT 1 | 0=离职,1=在职 |

**班主任判断**：`class.head_teacher_id = teacher.id`

## teacher_class — 教师任课关联
`(teacher_id, class_id, subject_id)` UNIQUE

---

## subject — 科目

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_id | BIGINT NOT NULL | → school.id |
| subject_name | VARCHAR(50) NOT NULL | |
| subject_code | VARCHAR(20) | |
| full_score | DECIMAL(5,1) DEFAULT 100 | 满分 |
| pass_score | DECIMAL(5,1) DEFAULT 60 | 及格线 |

---

## exam — 考试

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| school_id | BIGINT NOT NULL | → school.id |
| term_id | BIGINT NOT NULL | → academic_term.id |
| exam_name | VARCHAR(100) NOT NULL | |
| exam_type | SMALLINT | 1=月考,2=期中,3=期末,4=模拟,5=其他 |
| exam_date | DATE | |
| grade_ids | VARCHAR(500) | JSON 数组，参考年级ID |
| status | SMALLINT DEFAULT 0 | 0=未开始,1=录入,2=审核,3=已发布 |

## exam_subject — 考试科目
`(exam_id, subject_id)` UNIQUE，`full_score` 该场考试满分（可覆盖科目默认值）

---

## score — 成绩（核心事实表）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| exam_id | BIGINT NOT NULL | → exam.id |
| student_id | BIGINT NOT NULL | → student.id |
| subject_id | BIGINT NOT NULL | → subject.id |
| class_id | BIGINT NOT NULL | → class.id（冗余，加速查询） |
| school_id | BIGINT NOT NULL | → school.id（冗余，租户隔离） |
| score | DECIMAL(5,1) | 缺考为 NULL |
| is_absent | SMALLINT DEFAULT 0 | 0=正常,1=缺考 |
| rank_in_class | INT | 班级排名（预计算） |
| rank_in_grade | INT | 年级排名（预计算） |
| rank_in_school | INT | 校级排名（预计算） |
| total_rank_in_class | INT | 总分班级排名（同一学生同场考试所有行相同） |
| total_rank_in_grade | INT | 总分年级排名（同一学生同场考试所有行相同） |
| total_rank_in_school | INT | 总分校级排名（同一学生同场考试所有行相同） |
| recorder_id | BIGINT | → sys_user.id（录入人） |
| status | SMALLINT DEFAULT 0 | 0=待审核,1=已通过,2=已驳回 |
| recorded_at | TIMESTAMP | |
| reviewed_at | TIMESTAMP | |
| reviewer_id | BIGINT | → sys_user.id（审核人，V16；通过/驳回时写入） |
| reject_reason | VARCHAR(500) | 驳回原因（V16；status=2 时有值，重新通过时清空） |
| remark | VARCHAR(500) | 录入人备注（V16 之前驳回原因曾以「【驳回】」前缀暂存于此，历史数据未迁移） |

唯一索引：`(exam_id, student_id, subject_id, deleted)`

---

## score_change_log — 成绩修改申请

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| score_id | BIGINT NOT NULL | → score.id |
| exam_id / student_id / subject_id | BIGINT | 冗余，便于查询 |
| old_score / new_score | DECIMAL(5,1) | |
| change_reason | VARCHAR(500) | |
| applicant_id | BIGINT NOT NULL | 申请人 → sys_user.id |
| approver_id | BIGINT | 审批人 → sys_user.id |
| status | SMALLINT DEFAULT 0 | 0=待审,1=通过,2=驳回 |
| reject_reason | VARCHAR(500) | |

---

## score_statistics — 成绩统计缓存

按 `(exam_id, dimension_type, dimension_id, subject_id)` 唯一。
`dimension_type`: 1=班级,2=年级,3=学校,4=区域。
`statistics_data`: JSONB，分数段分布等详细数据。无软删除字段。

---

## notification — 系统通知

`type`: 1=成绩发布,2=成绩预警,3=系统通知。`is_read`: 0=未读,1=已读。

---

## parent_student — 家长学生关联
`parent_user_id` → `sys_user.id`（user_type=5 的家长账号），`student_id` → `student.id`；`relationship` 取字典 `relationship` 的 dict_value（father/mother/other）。V18 起有 `deleted`（0/1 软删，查询须加 `deleted = 0`）与 `updated_at`；唯一键 `(parent_user_id, student_id)` 仅对 `deleted = 0` 生效；索引 `idx_parent_student_parent(parent_user_id)`、`idx_parent_student_student(student_id)`。表无 school_id，同校约束在绑定时校验（家长 `sys_user.school_id` = `student.school_id`）。家长登录后 `childStudentIds` 由此表装载，只能查看这些学生的成绩/统计/成绩单，并与学生同收成绩发布与预警通知。

---

## sys_dict_type / sys_dict_data — 字典
`sys_dict_data.dict_type` → `sys_dict_type.dict_type`；`dict_value` 一律是字符串，`status` 0=正常,1=停用
已有类型（V7/V17 种子）：`exam_type`(1月考/2期中考试/3期末考试/4模拟考试/5其他，exam.exam_type 的展示名优先取此处)、`score_status`(0待审核/1已通过/2已驳回)、`user_status`(0正常/1停用)、`gender`(1男/2女)、`student_status`(0离校/1在读/2休学)、`relationship`(father父亲/mother母亲/other其他)、`region`(区划代码→名称，示例 440300 深圳市/440100 广州市)

## sys_config — 系统配置
全系统共享（无 school_id）。关键配置项：`score.warning.threshold`=20（成绩下降超过该分值触发预警），`score.pass.line`=60 / `score.excellent.line`=85（百分数，统计模块按 值/100 × 满分 折算及格/优秀线），`score.change.approval`=true（false 时管理员的成绩修改申请免审批直接生效，教师仍需审批），`system.maintenance`=false（true 时仅超管/校管理员可登录）

## sys_operation_log — 操作日志
仅追加，无软删除、无 school_id。由 `OperationLogAspect` 自动记录所有 Controller 的 POST/PUT/PATCH/DELETE 请求。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT | → sys_user.id；未登录请求（登录失败）为 NULL |
| username | VARCHAR(50) | 登录接口取请求参数里的 username |
| operation | VARCHAR(100) | 操作描述，如 `[score] 审核成绩`，未加注解时为 `Controller#method` |
| method | VARCHAR(200) | `HTTP方法 URI`，如 `POST /api/v1/scores/review` |
| params | TEXT | 请求参数 JSON，password/token/secret/authorization 字段已打码为 ***，截 2000 |
| ip | VARCHAR(50) | X-Forwarded-For 优先 |
| location | VARCHAR(100) | 预留，未填 |
| status | SMALLINT | 1=成功, 0=失败（含被 @PreAuthorize 拒绝的越权尝试） |
| error_msg | TEXT | 失败时异常消息，截 500 |
| execute_time | BIGINT | 耗时毫秒 |
| created_at | TIMESTAMP | |

校管理员查询时按 `JOIN sys_user u ON u.id = l.user_id AND u.school_id = <本校>` 过滤。

## sys_job — 定时任务定义
无 school_id（任务以系统身份跨校执行），仅超管可管理（`/jobs`）。启动时 `JobScheduler` 加载 `status=1` 的行，用 Spring TaskScheduler + CronTrigger 注册（不用 Quartz）。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| job_name | VARCHAR(100) | 任务名称 |
| job_code | VARCHAR(50) | 任务编码，`WHERE deleted=0` 范围内唯一（部分唯一索引），调度器以它为 key |
| handler_bean | VARCHAR(100) | 执行器 Spring Bean 名，须实现 `com.school.module.job.spi.JobHandler` |
| cron | VARCHAR(50) | Spring 6 位 cron：秒 分 时 日 月 周，如 `0 0 3 * * ?` |
| params | TEXT | 传给 handler 的 JSON 对象，如 `{"retainDays":90}` |
| status | SMALLINT | 0=停用, 1=启用 |
| last_run_at | TIMESTAMP | 最近一次执行结束时间 |
| last_run_status | SMALLINT | 最近一次结果：1=成功, 0=失败 |
| next_run_at | TIMESTAMP | 按 cron 算出的下次触发时间，停用时为 NULL |
| remark | VARCHAR(500) | |
| created_at / updated_at / deleted | | 软删除 |

预置（V20，均停用）：`cleanOperationLogs`→`cleanOperationLogsHandler`（每天 03:00，`{"retainDays":90}`，物理删除 N 天前的 sys_operation_log）、`scoreWarningScan`→`scoreWarningScanHandler`（每天 06:00，`{"days":7}`，对近 N 天已发布考试补发成绩预警，已发过不重发）、`backupDatabase`→`backupJobHandler`（每天 02:00，由备份模块提供，缺失时记失败日志）。

## sys_job_log — 定时任务执行日志
只追加，无软删除、无 school_id。每次执行（cron 触发或手动「立即执行」）一行。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| job_id | BIGINT | → sys_job.id |
| job_code | VARCHAR(50) | 冗余任务编码，任务删除后仍可读 |
| started_at | TIMESTAMP | |
| finished_at | TIMESTAMP | 执行中为 NULL |
| status | SMALLINT | 1=成功, 0=失败, 2=执行中（手动触发先插行再异步跑，接口据此返回 jobLogId） |
| message | TEXT | handler 返回的摘要或异常消息，截 2000 |
| duration_ms | BIGINT | 耗时毫秒 |
| triggered_by | VARCHAR(50) | `cron` 表示调度触发，否则为手动触发者用户名 |

## sys_backup — 数据备份记录
无 school_id，仅超级管理员可见（`/backups`）。文件落在 `app.backup.dir`，表里只存元数据。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| file_name | VARCHAR(255) NOT NULL | `.dump` = pg_dump -Fc 产物（需 pg_restore）；`.sql.gz` = 本应用 JDBC 生成，可在线恢复 |
| file_path | VARCHAR(500) | 文件绝对路径 |
| file_size | BIGINT | 字节数 |
| backup_type | SMALLINT | 1=手动（含上传导入、恢复前自动备份），2=定时任务 |
| backup_mode | VARCHAR(20) | 实际策略：pg_dump / docker / sql / upload |
| status | SMALLINT | 0=进行中, 1=成功, 2=失败 |
| message | TEXT | 失败原因或备注（「恢复前自动备份」「上传导入：原文件名」、行数摘要） |
| triggered_by | VARCHAR(50) | 用户名；定时任务为 `job` |
| started_at / finished_at | TIMESTAMP | |
| created_at / updated_at / deleted | | 软删除 |

在线恢复只 TRUNCATE 并重装「备份文件里有 ∩ 当前库里有」的表，永远排除 flyway_schema_history、sys_backup、sys_operation_log。

---

## 关系总览

```
school
  ├── sys_user (school_id)
  ├── academic_term (school_id)
  ├── grade (school_id)
  │     └── class (grade_id) ── head_teacher_id → teacher
  │           └── class_student → student
  ├── subject (school_id)
  ├── teacher (school_id)
  │     └── teacher_class → (class, subject)
  ├── student (school_id) → sys_user
  └── exam (school_id, term_id)
        ├── exam_subject → subject
        └── score (exam_id, student_id, subject_id, class_id, school_id)
              └── score_change_log
```
