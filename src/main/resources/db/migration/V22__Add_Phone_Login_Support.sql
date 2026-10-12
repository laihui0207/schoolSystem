-- V22: 手机号 + 短信验证码 登录/注册支持
-- sys_user.phone 自 V1 起已是 UNIQUE 且有 idx_user_phone，手机号查用户无需新索引；
-- 这里只补一个"手机号已验证"标记：短信注册 / 短信登录成功时置 1，管理员录入的手机号默认 0。

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone_verified SMALLINT DEFAULT 0;

COMMENT ON COLUMN sys_user.phone_verified IS '手机号是否已验证: 0-否 1-是（短信注册/登录成功置 1）';
