-- 成绩变更日志表
CREATE TABLE IF NOT EXISTS score_change_log (
    id BIGSERIAL PRIMARY KEY,
    score_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    old_score DECIMAL(5,1),
    new_score DECIMAL(5,1),
    change_reason VARCHAR(500),
    applicant_id BIGINT NOT NULL,
    approver_id BIGINT,
    status SMALLINT DEFAULT 0,  -- 0-待审批，1-已通过，2-已驳回
    apply_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approve_time TIMESTAMP,
    reject_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_change_score ON score_change_log(score_id);
CREATE INDEX idx_change_student ON score_change_log(student_id);
CREATE INDEX idx_change_exam ON score_change_log(exam_id);
CREATE INDEX idx_change_status ON score_change_log(status);

-- 添加注释
COMMENT ON TABLE score_change_log IS '成绩变更日志表';
COMMENT ON COLUMN score_change_log.status IS '状态：0-待审批，1-已通过，2-已驳回';
