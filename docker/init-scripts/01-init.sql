-- 初始化数据库脚本
-- 设置时区
SET TIME ZONE 'Asia/Shanghai';

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 创建schema用于管理不同的数据分组（可选）
-- CREATE SCHEMA IF NOT EXISTS school_data;

-- 授权
GRANT ALL PRIVILEGES ON DATABASE school_system TO school_admin;

-- 集成测试专用库（与 school_system 隔离，避免测试污染开发数据；见 application-test.yml）
CREATE DATABASE school_system_test;
GRANT ALL PRIVILEGES ON DATABASE school_system_test TO school_admin;
