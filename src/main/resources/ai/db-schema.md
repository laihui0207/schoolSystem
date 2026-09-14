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

---

## sys_role — 角色

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| role_code | VARCHAR(50) UNIQUE NOT NULL | e.g. ROLE_TEACHER |
| role_name | VARCHAR(50) NOT NULL | |
| school_id | BIGINT | NULL=平台级角色 |

## sys_user_role — 用户角色关联
`(user_id, role_id)` UNIQUE

## sys_permission — 权限
`permission_code` UNIQUE，`resource_type`: 1=菜单,2=按钮,3=API

## sys_role_permission — 角色权限关联
`(role_id, permission_id)` UNIQUE

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
| recorder_id | BIGINT | → sys_user.id（录入人） |
| status | SMALLINT DEFAULT 0 | 0=待审核,1=已通过,2=已驳回 |
| recorded_at | TIMESTAMP | |
| reviewed_at | TIMESTAMP | |
| remark | VARCHAR(500) | |

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
`(parent_user_id, student_id)` UNIQUE，`relationship` 如"父亲/母亲/监护人"

---

## sys_dict_type / sys_dict_data — 字典
`sys_dict_data.dict_type` → `sys_dict_type.dict_type`

## sys_config — 系统配置
关键配置项：`score.warning.threshold`=20，`score.pass.line`=60，`score.excellent.line`=85

## sys_operation_log — 操作日志
仅追加，无软删除。记录 `user_id, operation, method, params, ip, status, execute_time`。

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
