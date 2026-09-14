-- ============================
-- 学生成绩管理系统 - 成绩管理表
-- ============================

-- 考试表
CREATE TABLE IF NOT EXISTS exam (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    exam_name VARCHAR(100) NOT NULL,
    exam_type SMALLINT NOT NULL,
    exam_date DATE,
    grade_ids VARCHAR(500),
    status SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE exam IS '考试表';
COMMENT ON COLUMN exam.id IS '主键ID';
COMMENT ON COLUMN exam.school_id IS '学校ID';
COMMENT ON COLUMN exam.term_id IS '学期ID';
COMMENT ON COLUMN exam.exam_name IS '考试名称，如: 2025年秋季期中考试';
COMMENT ON COLUMN exam.exam_type IS '考试类型: 1-月考 2-期中 3-期末 4-模拟 5-其他';
COMMENT ON COLUMN exam.exam_date IS '考试日期';
COMMENT ON COLUMN exam.grade_ids IS '参与年级ID列表(JSON数组)';
COMMENT ON COLUMN exam.status IS '状态: 0-未开始 1-录入中 2-审核中 3-已发布';
COMMENT ON COLUMN exam.created_at IS '创建时间';
COMMENT ON COLUMN exam.updated_at IS '更新时间';
COMMENT ON COLUMN exam.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_exam_school ON exam(school_id);
CREATE INDEX idx_exam_term ON exam(term_id);
CREATE INDEX idx_exam_date ON exam(exam_date);
CREATE INDEX idx_exam_status ON exam(status);

-- 考试-科目关联表
CREATE TABLE IF NOT EXISTS exam_subject (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    full_score DECIMAL(5,1) DEFAULT 100,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE exam_subject IS '考试科目关联表';
COMMENT ON COLUMN exam_subject.id IS '主键ID';
COMMENT ON COLUMN exam_subject.exam_id IS '考试ID';
COMMENT ON COLUMN exam_subject.subject_id IS '科目ID';
COMMENT ON COLUMN exam_subject.full_score IS '该科目本次考试满分';
COMMENT ON COLUMN exam_subject.sort_order IS '排序';
COMMENT ON COLUMN exam_subject.created_at IS '创建时间';
COMMENT ON COLUMN exam_subject.updated_at IS '更新时间';
COMMENT ON COLUMN exam_subject.deleted IS '删除标记';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_exam_subject ON exam_subject(exam_id, subject_id);
CREATE INDEX idx_es_subject ON exam_subject(subject_id);

-- 成绩表（核心大表）
CREATE TABLE IF NOT EXISTS score (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    score DECIMAL(5,1),
    is_absent SMALLINT DEFAULT 0,
    rank_in_class INT,
    rank_in_grade INT,
    rank_in_school INT,
    recorder_id BIGINT,
    status SMALLINT DEFAULT 0,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE score IS '成绩表';
COMMENT ON COLUMN score.id IS '主键ID';
COMMENT ON COLUMN score.exam_id IS '考试ID';
COMMENT ON COLUMN score.student_id IS '学生ID';
COMMENT ON COLUMN score.subject_id IS '科目ID';
COMMENT ON COLUMN score.class_id IS '班级ID';
COMMENT ON COLUMN score.school_id IS '学校ID';
COMMENT ON COLUMN score.score IS '得分';
COMMENT ON COLUMN score.is_absent IS '是否缺考: 0-否 1-是';
COMMENT ON COLUMN score.rank_in_class IS '班级排名';
COMMENT ON COLUMN score.rank_in_grade IS '年级排名';
COMMENT ON COLUMN score.rank_in_school IS '校级排名';
COMMENT ON COLUMN score.recorder_id IS '录入人ID';
COMMENT ON COLUMN score.status IS '状态: 0-待审核 1-已通过 2-已驳回';
COMMENT ON COLUMN score.recorded_at IS '录入时间';
COMMENT ON COLUMN score.reviewed_at IS '审核时间';
COMMENT ON COLUMN score.created_at IS '创建时间';
COMMENT ON COLUMN score.updated_at IS '更新时间';
COMMENT ON COLUMN score.deleted IS '删除标记';

-- 创建唯一索引和普通索引
CREATE UNIQUE INDEX uk_exam_student_subject ON score(exam_id, student_id, subject_id) WHERE deleted = 0;
CREATE INDEX idx_score_school_exam ON score(school_id, exam_id);
CREATE INDEX idx_score_class_exam ON score(class_id, exam_id);
CREATE INDEX idx_score_student ON score(student_id);
CREATE INDEX idx_score_exam ON score(exam_id);
CREATE INDEX idx_score_status ON score(status);

-- 成绩变更日志表
CREATE TABLE IF NOT EXISTS score_change_log (
    id BIGSERIAL PRIMARY KEY,
    score_id BIGINT NOT NULL,
    old_score DECIMAL(5,1),
    new_score DECIMAL(5,1),
    change_reason VARCHAR(500),
    operator_id BIGINT NOT NULL,
    approve_status SMALLINT DEFAULT 0,
    approver_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP
);

COMMENT ON TABLE score_change_log IS '成绩变更日志表';
COMMENT ON COLUMN score_change_log.id IS '主键ID';
COMMENT ON COLUMN score_change_log.score_id IS '成绩ID';
COMMENT ON COLUMN score_change_log.old_score IS '原分数';
COMMENT ON COLUMN score_change_log.new_score IS '新分数';
COMMENT ON COLUMN score_change_log.change_reason IS '变更原因';
COMMENT ON COLUMN score_change_log.operator_id IS '操作人ID';
COMMENT ON COLUMN score_change_log.approve_status IS '审批状态: 0-待审批 1-通过 2-拒绝';
COMMENT ON COLUMN score_change_log.approver_id IS '审批人ID';
COMMENT ON COLUMN score_change_log.created_at IS '创建时间';
COMMENT ON COLUMN score_change_log.approved_at IS '审批时间';

-- 创建索引
CREATE INDEX idx_change_log_score ON score_change_log(score_id);
CREATE INDEX idx_change_log_operator ON score_change_log(operator_id);
CREATE INDEX idx_change_log_status ON score_change_log(approve_status);

-- 成绩统计表（缓存表，存储预计算的统计数据）
CREATE TABLE IF NOT EXISTS score_statistics (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    dimension_type SMALLINT NOT NULL,
    dimension_id BIGINT NOT NULL,
    subject_id BIGINT,
    total_count INT,
    average_score DECIMAL(6,2),
    max_score DECIMAL(5,1),
    min_score DECIMAL(5,1),
    pass_count INT,
    pass_rate DECIMAL(5,2),
    excellent_count INT,
    excellent_rate DECIMAL(5,2),
    statistics_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE score_statistics IS '成绩统计表';
COMMENT ON COLUMN score_statistics.id IS '主键ID';
COMMENT ON COLUMN score_statistics.exam_id IS '考试ID';
COMMENT ON COLUMN score_statistics.dimension_type IS '统计维度: 1-班级 2-年级 3-学校 4-区域';
COMMENT ON COLUMN score_statistics.dimension_id IS '维度ID（如班级ID、年级ID等）';
COMMENT ON COLUMN score_statistics.subject_id IS '科目ID，NULL表示总分统计';
COMMENT ON COLUMN score_statistics.total_count IS '总人数';
COMMENT ON COLUMN score_statistics.average_score IS '平均分';
COMMENT ON COLUMN score_statistics.max_score IS '最高分';
COMMENT ON COLUMN score_statistics.min_score IS '最低分';
COMMENT ON COLUMN score_statistics.pass_count IS '及格人数';
COMMENT ON COLUMN score_statistics.pass_rate IS '及格率';
COMMENT ON COLUMN score_statistics.excellent_count IS '优秀人数';
COMMENT ON COLUMN score_statistics.excellent_rate IS '优秀率';
COMMENT ON COLUMN score_statistics.statistics_data IS '详细统计数据(JSON格式)，如分数段分布';
COMMENT ON COLUMN score_statistics.created_at IS '创建时间';
COMMENT ON COLUMN score_statistics.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX idx_stat_exam ON score_statistics(exam_id);
CREATE INDEX idx_stat_dimension ON score_statistics(dimension_type, dimension_id);
CREATE INDEX idx_stat_subject ON score_statistics(subject_id);
CREATE UNIQUE INDEX uk_stat_unique ON score_statistics(exam_id, dimension_type, dimension_id, COALESCE(subject_id, 0));
