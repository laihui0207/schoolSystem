-- 成绩变更日志表
-- ponytail: V3 里残留了一份旧版 score_change_log（operator_id/approve_status 列），全新建库时会先被 V3 建出来，
-- 导致下面的 CREATE TABLE IF NOT EXISTS 被跳过、student_id 索引失败。此时该表必然为空（V4/V5 不写它），
-- 所以直接丢弃后按实体类 ScoreChangeLog 的结构重建。已存在的库早已跑过 V6，不受影响。
DROP TABLE IF EXISTS score_change_log;
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
