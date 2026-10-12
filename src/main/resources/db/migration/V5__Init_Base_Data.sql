-- ============================
-- 学生成绩管理系统 - 初始化基础数据
-- ============================

-- 插入默认角色
INSERT INTO sys_role (role_code, role_name, description, school_id) VALUES
('ROLE_SUPER_ADMIN', '超级管理员', '拥有系统所有权限', NULL),
('ROLE_SCHOOL_ADMIN', '学校管理员', '管理本校所有数据', NULL),
('ROLE_TEACHER', '教师', '管理所授班级的成绩', NULL),
('ROLE_STUDENT', '学生', '查看个人成绩', NULL),
('ROLE_PARENT', '家长', '查看孩子成绩', NULL);

-- 插入超级管理员用户（密码: admin123）
-- BCrypt加密后的密码
INSERT INTO sys_user (username, password, phone, real_name, user_type, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKLV.dDu', '13800138000', '系统管理员', 1, 1);

-- 获取刚插入的用户ID和角色ID，并建立关联
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_SUPER_ADMIN';

-- 插入默认权限（简化版，实际应该更详细）
INSERT INTO sys_permission (permission_code, permission_name, resource_type, url, parent_id, sort_order) VALUES
-- 系统管理
('sys:manage', '系统管理', 1, NULL, NULL, 1),
('sys:user:list', '用户列表', 2, '/sys/user/list', 1, 11),
('sys:user:add', '添加用户', 3, '/sys/user/add', 1, 12),
('sys:user:edit', '编辑用户', 3, '/sys/user/edit', 1, 13),
('sys:user:delete', '删除用户', 3, '/sys/user/delete', 1, 14),

-- 学校管理
('school:manage', '学校管理', 1, NULL, NULL, 2),
('school:list', '学校列表', 2, '/school/list', 6, 21),
('school:add', '添加学校', 3, '/school/add', 6, 22),
('school:edit', '编辑学校', 3, '/school/edit', 6, 23),

-- 班级管理
('class:manage', '班级管理', 1, NULL, NULL, 3),
('class:list', '班级列表', 2, '/class/list', 10, 31),
('class:add', '添加班级', 3, '/class/add', 10, 32),
('class:edit', '编辑班级', 3, '/class/edit', 10, 33),

-- 成绩管理
('score:manage', '成绩管理', 1, NULL, NULL, 4),
('score:input', '成绩录入', 2, '/score/input', 14, 41),
('score:review', '成绩审核', 2, '/score/review', 14, 42),
('score:publish', '成绩发布', 2, '/score/publish', 14, 43),
('score:query', '成绩查询', 2, '/score/query', 14, 44),

-- 统计分析
('stat:manage', '统计分析', 1, NULL, NULL, 5),
('stat:class', '班级统计', 2, '/stat/class', 19, 51),
('stat:grade', '年级统计', 2, '/stat/grade', 19, 52),
('stat:school', '学校统计', 2, '/stat/school', 19, 53);

-- 为超级管理员角色分配所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_SUPER_ADMIN';
