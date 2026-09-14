-- 删除现有的admin用户（如果存在）
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = 'admin');
DELETE FROM sys_user WHERE username = 'admin';

-- 重新插入admin用户
-- 密码: admin123
-- BCrypt加密后的密码
INSERT INTO sys_user (username, password, phone, real_name, user_type, status, deleted)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKLV.dDu', '13800138000', '系统管理员', 1, 1, 0);

-- 为admin用户分配超级管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_SUPER_ADMIN';

-- 验证插入结果
SELECT id, username, real_name, user_type, status FROM sys_user WHERE username = 'admin';
