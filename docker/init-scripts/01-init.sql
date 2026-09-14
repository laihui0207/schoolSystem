-- 初始化数据库脚本
-- 设置时区
SET TIME ZONE 'Asia/Shanghai';

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 创建schema用于管理不同的数据分组（可选）
-- CREATE SCHEMA IF NOT EXISTS school_data;

-- 授权
GRANT ALL PRIVILEGES ON DATABASE school_system TO school_admin;
