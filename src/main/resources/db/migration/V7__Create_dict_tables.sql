-- 数据字典类型表
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGSERIAL PRIMARY KEY,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL UNIQUE,
    status SMALLINT DEFAULT 0,  -- 0-正常，1-停用
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

-- 数据字典数据表
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGSERIAL PRIMARY KEY,
    dict_sort INTEGER DEFAULT 0,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    is_default SMALLINT DEFAULT 0,  -- 是否默认：0-否，1-是
    status SMALLINT DEFAULT 0,  -- 0-正常，1-停用
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_dict_type ON sys_dict_data(dict_type);
CREATE INDEX idx_dict_status ON sys_dict_data(status);

-- 添加注释
COMMENT ON TABLE sys_dict_type IS '字典类型表';
COMMENT ON TABLE sys_dict_data IS '字典数据表';

-- 插入示例数据
INSERT INTO sys_dict_type (dict_name, dict_type, status, remark) VALUES
('考试类型', 'exam_type', 0, '考试类型列表'),
('成绩状态', 'score_status', 0, '成绩状态列表'),
('用户状态', 'user_status', 0, '用户状态列表');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status) VALUES
(1, '月考', '1', 'exam_type', 0),
(2, '期中考试', '2', 'exam_type', 0),
(3, '期末考试', '3', 'exam_type', 0),
(4, '模拟考试', '4', 'exam_type', 0),
(5, '其他', '5', 'exam_type', 0),
(1, '待审核', '0', 'score_status', 0),
(2, '已通过', '1', 'score_status', 0),
(3, '已驳回', '2', 'score_status', 0),
(1, '正常', '0', 'user_status', 0),
(2, '停用', '1', 'user_status', 0);
