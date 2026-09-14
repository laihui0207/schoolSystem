-- ============================
-- 学生成绩管理系统 - 基础表
-- ============================

-- 学校表
CREATE TABLE IF NOT EXISTS school (
    id BIGSERIAL PRIMARY KEY,
    school_code VARCHAR(32) UNIQUE NOT NULL,
    school_name VARCHAR(100) NOT NULL,
    region_code VARCHAR(20),
    address VARCHAR(255),
    contact_phone VARCHAR(20),
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE school IS '学校表';
COMMENT ON COLUMN school.id IS '主键ID';
COMMENT ON COLUMN school.school_code IS '学校编码';
COMMENT ON COLUMN school.school_name IS '学校名称';
COMMENT ON COLUMN school.region_code IS '区域编码';
COMMENT ON COLUMN school.address IS '学校地址';
COMMENT ON COLUMN school.contact_phone IS '联系电话';
COMMENT ON COLUMN school.status IS '状态: 0-禁用 1-启用';
COMMENT ON COLUMN school.created_at IS '创建时间';
COMMENT ON COLUMN school.updated_at IS '更新时间';
COMMENT ON COLUMN school.deleted IS '删除标记: 0-未删除 1-已删除';

-- 创建索引
CREATE INDEX idx_school_code ON school(school_code);
CREATE INDEX idx_school_region ON school(region_code);
CREATE INDEX idx_school_status ON school(status);

-- 用户表（统一用户）
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(200) NOT NULL,
    phone VARCHAR(20) UNIQUE,
    real_name VARCHAR(50),
    avatar VARCHAR(500),
    user_type SMALLINT NOT NULL,
    school_id BIGINT,
    status SMALLINT DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.id IS '主键ID';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码(BCrypt加密)';
COMMENT ON COLUMN sys_user.phone IS '手机号';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.avatar IS '头像URL';
COMMENT ON COLUMN sys_user.user_type IS '用户类型: 1-超级管理员 2-学校管理员 3-教师 4-学生 5-家长';
COMMENT ON COLUMN sys_user.school_id IS '所属学校ID';
COMMENT ON COLUMN sys_user.status IS '状态: 0-禁用 1-启用';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';
COMMENT ON COLUMN sys_user.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_user_username ON sys_user(username);
CREATE INDEX idx_user_phone ON sys_user(phone);
CREATE INDEX idx_user_type ON sys_user(user_type);
CREATE INDEX idx_user_school ON sys_user(school_id);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) UNIQUE NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    school_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.id IS '主键ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.description IS '角色描述';
COMMENT ON COLUMN sys_role.school_id IS '所属学校ID，NULL表示平台级角色';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_role.updated_at IS '更新时间';
COMMENT ON COLUMN sys_role.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_role_code ON sys_role(role_code);
CREATE INDEX idx_role_school ON sys_role(school_id);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.id IS '主键ID';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';
COMMENT ON COLUMN sys_user_role.created_at IS '创建时间';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_user_role ON sys_user_role(user_id, role_id);

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) UNIQUE NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    resource_type SMALLINT NOT NULL,
    url VARCHAR(200),
    parent_id BIGINT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE sys_permission IS '权限表';
COMMENT ON COLUMN sys_permission.id IS '主键ID';
COMMENT ON COLUMN sys_permission.permission_code IS '权限编码';
COMMENT ON COLUMN sys_permission.permission_name IS '权限名称';
COMMENT ON COLUMN sys_permission.resource_type IS '资源类型: 1-菜单 2-按钮 3-接口';
COMMENT ON COLUMN sys_permission.url IS '资源URL';
COMMENT ON COLUMN sys_permission.parent_id IS '父权限ID';
COMMENT ON COLUMN sys_permission.sort_order IS '排序';
COMMENT ON COLUMN sys_permission.created_at IS '创建时间';
COMMENT ON COLUMN sys_permission.updated_at IS '更新时间';
COMMENT ON COLUMN sys_permission.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_permission_code ON sys_permission(permission_code);
CREATE INDEX idx_permission_parent ON sys_permission(parent_id);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_role_permission IS '角色权限关联表';
COMMENT ON COLUMN sys_role_permission.id IS '主键ID';
COMMENT ON COLUMN sys_role_permission.role_id IS '角色ID';
COMMENT ON COLUMN sys_role_permission.permission_id IS '权限ID';
COMMENT ON COLUMN sys_role_permission.created_at IS '创建时间';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_role_permission ON sys_role_permission(role_id, permission_id);

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    operation VARCHAR(100),
    method VARCHAR(200),
    params TEXT,
    ip VARCHAR(50),
    location VARCHAR(100),
    status SMALLINT,
    error_msg TEXT,
    execute_time BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_operation_log IS '操作日志表';
COMMENT ON COLUMN sys_operation_log.id IS '主键ID';
COMMENT ON COLUMN sys_operation_log.user_id IS '用户ID';
COMMENT ON COLUMN sys_operation_log.username IS '用户名';
COMMENT ON COLUMN sys_operation_log.operation IS '操作描述';
COMMENT ON COLUMN sys_operation_log.method IS '请求方法';
COMMENT ON COLUMN sys_operation_log.params IS '请求参数';
COMMENT ON COLUMN sys_operation_log.ip IS 'IP地址';
COMMENT ON COLUMN sys_operation_log.location IS '操作地点';
COMMENT ON COLUMN sys_operation_log.status IS '操作状态: 0-失败 1-成功';
COMMENT ON COLUMN sys_operation_log.error_msg IS '错误信息';
COMMENT ON COLUMN sys_operation_log.execute_time IS '执行时间(毫秒)';
COMMENT ON COLUMN sys_operation_log.created_at IS '创建时间';

-- 创建索引
CREATE INDEX idx_log_user ON sys_operation_log(user_id);
CREATE INDEX idx_log_created ON sys_operation_log(created_at);
