-- 修复 admin 初始密码
-- V5__Init_Base_Data.sql 中的哈希并非 admin123 的 BCrypt 值，导致登录始终 Bad credentials
-- 下面的哈希由项目自身的 BCryptPasswordEncoder 生成，对应密码: admin123
UPDATE sys_user
SET password = '$2a$10$YxYAcxyRlruIRyDzVV0Ct.xR2IJ681EDHPucf0sGPT9F1HrLfEt9q'
WHERE username = 'admin';
