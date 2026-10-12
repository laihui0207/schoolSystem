-- ============================
-- V19: RBAC 角色内置标记 + 权限树补齐 + 默认角色权限
-- ============================
-- 全部用 IF NOT EXISTS / INSERT ... SELECT ... WHERE NOT EXISTS 保证幂等。
-- ponytail: V5 的权限用硬编码 parent_id（1/6/10/14/19），这里一律通过 permission_code 子查询解析父节点，
-- 不依赖自增序列的历史值；已被管理员软删除（deleted=1）的权限不会被重新插入（permission_code UNIQUE）。

-- ---------- sys_role.is_builtin ----------
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS is_builtin SMALLINT DEFAULT 0;
COMMENT ON COLUMN sys_role.is_builtin IS '是否内置角色: 0-否 1-是（内置角色不可删除、role_code 不可修改）';

UPDATE sys_role SET is_builtin = 1
WHERE role_code IN ('ROLE_SUPER_ADMIN', 'ROLE_SCHOOL_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_PARENT')
  AND COALESCE(is_builtin, 0) <> 1;

-- ---------- 一级菜单（resource_type=1） ----------
-- V5 已有：sys:manage(1) school:manage(2) class:manage(3) score:manage(4) stat:manage(5)
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'term:manage', '学期管理', 1, NULL, NULL, 6
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'term:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'grade:manage', '年级管理', 1, NULL, NULL, 7
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'grade:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'subject:manage', '科目管理', 1, NULL, NULL, 8
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'subject:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:manage', '学生管理', 1, NULL, NULL, 9
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:manage', '教师管理', 1, NULL, NULL, 10
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:manage', '考试管理', 1, NULL, NULL, 11
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:manage', '导入导出', 1, NULL, NULL, 12
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'notification:manage', '通知管理', 1, NULL, NULL, 13
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'notification:manage');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'warning:manage', '成绩预警', 1, NULL, NULL, 14
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'warning:manage');

-- ---------- 系统管理二级菜单（sys:manage 下按子模块分组，系统管理树统一为三层） ----------
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:user', '用户管理', 1, '/users', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:user');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:role', '角色管理', 1, '/roles', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:role');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:permission', '权限管理', 1, '/permissions', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:permission');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:dict', '数据字典', 1, '/dict', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:dict');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:config', '系统配置', 1, '/configs', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:config');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:log', '操作日志', 1, '/operation-logs', p.id, 6 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:log');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job', '定时任务', 1, '/jobs', p.id, 7 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:backup', '数据备份', 1, '/backups', p.id, 8 FROM sys_permission p
WHERE p.permission_code = 'sys:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:backup');

-- V5 把 sys:user:* 直接挂在 sys:manage 下，迁到新的 sys:user 二级节点；第二次执行时 parent_id 已不等于 sys:manage，自然跳过
UPDATE sys_permission SET parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'sys:user')
WHERE permission_code IN ('sys:user:list', 'sys:user:add', 'sys:user:edit', 'sys:user:delete')
  AND parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'sys:manage');

-- ---------- 叶子权限（resource_type 2=按钮/列表，3=接口动作）；url 为后端实际路径 ----------
-- V5 已有：sys:user:list/add/edit/delete, school:list/add/edit, class:list/add/edit,
--          score:input/review/publish/query, stat:class/grade/school
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'school:delete', '删除学校', 3, '/schools/{id}', p.id, 24 FROM sys_permission p
WHERE p.permission_code = 'school:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'school:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'term:list', '学期列表', 2, '/terms', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'term:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'term:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'term:add', '添加学期', 3, '/terms', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'term:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'term:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'term:edit', '编辑学期', 3, '/terms/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'term:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'term:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'term:delete', '删除学期', 3, '/terms/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'term:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'term:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'grade:list', '年级列表', 2, '/grades', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'grade:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'grade:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'grade:add', '添加年级', 3, '/grades', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'grade:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'grade:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'grade:edit', '编辑年级', 3, '/grades/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'grade:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'grade:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'grade:delete', '删除年级', 3, '/grades/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'grade:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'grade:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'class:delete', '删除班级', 3, '/classes/{id}', p.id, 34 FROM sys_permission p
WHERE p.permission_code = 'class:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'class:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'subject:list', '科目列表', 2, '/subjects', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'subject:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'subject:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'subject:add', '添加科目', 3, '/subjects', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'subject:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'subject:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'subject:edit', '编辑科目', 3, '/subjects/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'subject:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'subject:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'subject:delete', '删除科目', 3, '/subjects/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'subject:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'subject:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:list', '学生列表', 2, '/students', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:add', '添加学生', 3, '/students', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:edit', '编辑学生', 3, '/students/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:delete', '删除学生', 3, '/students/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:import', '导入学生', 3, '/students/import', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:import');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'student:export', '导出学生', 3, '/students/export', p.id, 6 FROM sys_permission p
WHERE p.permission_code = 'student:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'student:export');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:list', '教师列表', 2, '/teachers', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:add', '添加教师', 3, '/teachers', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:edit', '编辑教师', 3, '/teachers/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:delete', '删除教师', 3, '/teachers/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:import', '导入教师', 3, '/teachers/import', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:import');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'teacher:export', '导出教师', 3, '/teachers/export', p.id, 6 FROM sys_permission p
WHERE p.permission_code = 'teacher:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'teacher:export');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:list', '考试列表', 2, '/exams', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'exam:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:add', '创建考试', 3, '/exams', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'exam:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:edit', '编辑考试', 3, '/exams/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'exam:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:delete', '删除考试', 3, '/exams/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'exam:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'exam:status', '考试状态流转', 3, '/exams/{id}/status', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'exam:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'exam:status');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:batch-input', '批量录入成绩', 3, '/scores/batch-input', p.id, 45 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:batch-input');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:edit', '修改成绩', 3, '/scores/{id}', p.id, 46 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:delete', '删除成绩', 3, '/scores/{id}', p.id, 47 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:rank-calc', '重算排名', 3, '/scores/calculate-rankings', p.id, 48 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:rank-calc');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:change-apply', '申请修改成绩', 3, '/score-changes/apply', p.id, 49 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:change-apply');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'score:change-approve', '审批成绩修改', 3, '/score-changes/approve', p.id, 50 FROM sys_permission p
WHERE p.permission_code = 'score:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'score:change-approve');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'stat:personal', '个人统计', 2, '/stats/personal', p.id, 54 FROM sys_permission p
WHERE p.permission_code = 'stat:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'stat:personal');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'stat:subject', '科目统计', 2, '/stats/subject', p.id, 55 FROM sys_permission p
WHERE p.permission_code = 'stat:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'stat:subject');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'stat:region', '区域对比', 2, '/stats/region', p.id, 56 FROM sys_permission p
WHERE p.permission_code = 'stat:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'stat:region');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'stat:report', '统计报表', 2, '/export/stat-report', p.id, 57 FROM sys_permission p
WHERE p.permission_code = 'stat:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'stat:report');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:scores', '导出成绩', 3, '/export/scores', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'export:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:scores');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:import-scores', '导入成绩', 3, '/export/import-scores', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'export:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:import-scores');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:template', '下载模板', 3, '/export/template', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'export:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:template');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:pdf', '成绩单 PDF', 3, '/pdf/score-report', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'export:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:pdf');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'export:stat-report', '统计报表 Excel', 3, '/export/stat-report', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'export:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'export:stat-report');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'notification:list', '通知列表', 2, '/notifications', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'notification:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'notification:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'notification:add', '发送通知', 3, '/notifications', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'notification:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'notification:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'notification:delete', '删除通知', 3, '/notifications/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'notification:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'notification:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'warning:list', '预警列表', 2, '/warnings', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'warning:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'warning:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'warning:check', '执行预警检测', 3, '/warnings/check', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'warning:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'warning:check');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'warning:trend', '成绩趋势分析', 2, '/warnings/trend', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'warning:manage' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'warning:trend');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:user:assign-role', '分配用户角色', 3, '/users/{id}/roles', p.id, 15 FROM sys_permission p
WHERE p.permission_code = 'sys:user' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:user:assign-role');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:role:list', '角色列表', 2, '/roles', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:role' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:role:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:role:add', '添加角色', 3, '/roles', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:role' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:role:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:role:edit', '编辑角色/分配权限', 3, '/roles/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:role' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:role:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:role:delete', '删除角色', 3, '/roles/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:role' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:role:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:permission:list', '权限树', 2, '/permissions/tree', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:permission' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:permission:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:permission:add', '添加权限点', 3, '/permissions', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:permission' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:permission:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:permission:edit', '编辑权限点', 3, '/permissions/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:permission' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:permission:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:permission:delete', '删除权限点', 3, '/permissions/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:permission' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:permission:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:dict:list', '字典列表', 2, '/dict/types', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:dict' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:dict:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:dict:add', '添加字典', 3, '/dict/types', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:dict' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:dict:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:dict:edit', '编辑字典', 3, '/dict/types/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:dict' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:dict:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:dict:delete', '删除字典', 3, '/dict/types/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:dict' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:dict:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:config:list', '配置列表', 2, '/configs', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:config' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:config:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:config:add', '添加配置', 3, '/configs', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:config' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:config:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:config:edit', '编辑配置', 3, '/configs/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:config' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:config:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:config:delete', '删除配置', 3, '/configs/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:config' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:config:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:log:list', '查看操作日志', 2, '/operation-logs', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:log' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:log:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job:list', '任务列表', 2, '/jobs', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:job' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job:add', '添加任务', 3, '/jobs', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:job' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job:add');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job:edit', '编辑任务', 3, '/jobs/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:job' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job:edit');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job:delete', '删除任务', 3, '/jobs/{id}', p.id, 4 FROM sys_permission p
WHERE p.permission_code = 'sys:job' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job:delete');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:job:run', '立即执行任务', 3, '/jobs/{id}/run', p.id, 5 FROM sys_permission p
WHERE p.permission_code = 'sys:job' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:job:run');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:backup:list', '备份列表', 2, '/backups', p.id, 1 FROM sys_permission p
WHERE p.permission_code = 'sys:backup' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:backup:list');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:backup:run', '执行备份', 3, '/backups', p.id, 2 FROM sys_permission p
WHERE p.permission_code = 'sys:backup' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:backup:run');
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order)
SELECT 'sys:backup:delete', '删除备份', 3, '/backups/{id}', p.id, 3 FROM sys_permission p
WHERE p.permission_code = 'sys:backup' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'sys:backup:delete');

-- ============================
-- 默认角色权限
-- ============================
-- ponytail: 超管在 PermissionEvaluatorBean 里恒为 true，这里仍把全部权限点绑给 ROLE_SUPER_ADMIN，
-- 让 GET /roles/{id}/permissions 与前端权限树展示一致；V5 只绑了当时的 23 条。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         CROSS JOIN sys_permission p
WHERE r.role_code = 'ROLE_SUPER_ADMIN'
  AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 校管理员：本校全部业务 + 角色管理；不含学校增删、区域对比、配置/字典/任务/备份写操作、权限点维护
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         CROSS JOIN sys_permission p
WHERE r.role_code = 'ROLE_SCHOOL_ADMIN'
  AND p.deleted = 0
  AND p.permission_code IN (
    'sys:manage', 'sys:user', 'sys:user:list', 'sys:user:assign-role', 'sys:role', 'sys:role:list',
    'sys:role:add', 'sys:role:edit', 'sys:role:delete', 'sys:permission', 'sys:permission:list', 'sys:dict',
    'sys:dict:list', 'sys:config', 'sys:config:list', 'sys:log', 'sys:log:list', 'school:manage',
    'school:list', 'term:manage', 'term:list', 'term:add', 'term:edit', 'term:delete',
    'grade:manage', 'grade:list', 'grade:add', 'grade:edit', 'grade:delete', 'class:manage',
    'class:list', 'class:add', 'class:edit', 'class:delete', 'subject:manage', 'subject:list',
    'subject:add', 'subject:edit', 'subject:delete', 'student:manage', 'student:list', 'student:add',
    'student:edit', 'student:delete', 'student:import', 'student:export', 'teacher:manage', 'teacher:list',
    'teacher:add', 'teacher:edit', 'teacher:delete', 'teacher:import', 'teacher:export', 'exam:manage',
    'exam:list', 'exam:add', 'exam:edit', 'exam:delete', 'exam:status', 'score:manage',
    'score:input', 'score:batch-input', 'score:review', 'score:publish', 'score:query', 'score:edit',
    'score:delete', 'score:rank-calc', 'score:change-apply', 'score:change-approve', 'stat:manage', 'stat:class',
    'stat:grade', 'stat:school', 'stat:personal', 'stat:subject', 'stat:report', 'export:manage',
    'export:scores', 'export:import-scores', 'export:template', 'export:pdf', 'export:stat-report', 'notification:manage',
    'notification:list', 'notification:add', 'notification:delete', 'warning:manage', 'warning:list', 'warning:check',
    'warning:trend'
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 教师：任教班级范围内的录入/查询/统计/导出。score:review 不默认给——班主任由 class.head_teacher_id 动态决定，
-- 服务层已按班主任收窄；需要时由管理员建「班主任」自定义角色再授予
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         CROSS JOIN sys_permission p
WHERE r.role_code = 'ROLE_TEACHER'
  AND p.deleted = 0
  AND p.permission_code IN (
    'term:manage', 'term:list', 'grade:manage', 'grade:list', 'class:manage', 'class:list',
    'subject:manage', 'subject:list', 'student:manage', 'student:list', 'teacher:manage', 'teacher:list',
    'exam:manage', 'exam:list', 'score:manage', 'score:input', 'score:batch-input', 'score:query',
    'score:change-apply', 'stat:manage', 'stat:class', 'stat:grade', 'stat:subject', 'stat:personal',
    'stat:report', 'export:manage', 'export:scores', 'export:import-scores', 'export:template', 'export:pdf',
    'export:stat-report', 'notification:manage', 'notification:list', 'warning:manage', 'warning:list', 'warning:trend'
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 学生 / 家长：只读自身（家长读孩子）成绩、个人统计、成绩单 PDF、通知、趋势
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         CROSS JOIN sys_permission p
WHERE r.role_code IN ('ROLE_STUDENT', 'ROLE_PARENT')
  AND p.deleted = 0
  AND p.permission_code IN (
    'exam:manage', 'exam:list', 'score:manage', 'score:query', 'stat:manage', 'stat:personal',
    'export:manage', 'export:pdf', 'notification:manage', 'notification:list', 'warning:manage', 'warning:trend'
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
