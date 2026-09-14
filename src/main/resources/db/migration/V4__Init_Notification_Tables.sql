-- ============================
-- 学生成绩管理系统 - 通知消息表
-- ============================

-- 通知消息表
CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type SMALLINT NOT NULL,
    is_read SMALLINT DEFAULT 0,
    related_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE notification IS '通知消息表';
COMMENT ON COLUMN notification.id IS '主键ID';
COMMENT ON COLUMN notification.user_id IS '用户ID';
COMMENT ON COLUMN notification.title IS '通知标题';
COMMENT ON COLUMN notification.content IS '通知内容';
COMMENT ON COLUMN notification.type IS '通知类型: 1-成绩发布 2-成绩预警 3-系统通知';
COMMENT ON COLUMN notification.is_read IS '是否已读: 0-未读 1-已读';
COMMENT ON COLUMN notification.related_id IS '关联ID（如考试ID、成绩ID等）';
COMMENT ON COLUMN notification.created_at IS '创建时间';
COMMENT ON COLUMN notification.updated_at IS '更新时间';
COMMENT ON COLUMN notification.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_notify_user ON notification(user_id);
CREATE INDEX idx_notify_type ON notification(type);
CREATE INDEX idx_notify_read ON notification(is_read);
CREATE INDEX idx_notify_created ON notification(created_at);

-- 家长学生关联表
CREATE TABLE IF NOT EXISTS parent_student (
    id BIGSERIAL PRIMARY KEY,
    parent_user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    relationship VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE parent_student IS '家长学生关联表';
COMMENT ON COLUMN parent_student.id IS '主键ID';
COMMENT ON COLUMN parent_student.parent_user_id IS '家长用户ID';
COMMENT ON COLUMN parent_student.student_id IS '学生ID';
COMMENT ON COLUMN parent_student.relationship IS '关系: 父亲、母亲、监护人等';
COMMENT ON COLUMN parent_student.created_at IS '创建时间';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_parent_student ON parent_student(parent_user_id, student_id);
CREATE INDEX idx_ps_student ON parent_student(student_id);
