-- 系统参数配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500) NOT NULL,
    config_type SMALLINT DEFAULT 0,  -- 0-系统内置，1-用户自定义
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_config_key ON sys_config(config_key);

-- 添加注释
COMMENT ON TABLE sys_config IS '系统参数配置表';
COMMENT ON COLUMN sys_config.config_type IS '配置类型：0-系统内置，1-用户自定义';

-- 插入示例配置
INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark) VALUES
('成绩预警阈值', 'score.warning.threshold', '20', 1, '成绩下降超过该值时触发预警'),
('及格分数线', 'score.pass.line', '60', 0, '成绩及格分数线'),
('优秀分数线', 'score.excellent.line', '85', 0, '成绩优秀分数线'),
('成绩修改审批开关', 'score.change.approval', 'true', 1, '是否启用成绩修改审批流程'),
('系统维护模式', 'system.maintenance', 'false', 0, '系统维护模式开关');
