-- ============================
-- 学生成绩管理系统 - 学校基础数据表
-- ============================

-- 学年学期表
CREATE TABLE IF NOT EXISTS academic_term (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    year VARCHAR(20) NOT NULL,
    term SMALLINT NOT NULL,
    start_date DATE,
    end_date DATE,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE academic_term IS '学年学期表';
COMMENT ON COLUMN academic_term.id IS '主键ID';
COMMENT ON COLUMN academic_term.school_id IS '学校ID';
COMMENT ON COLUMN academic_term.year IS '学年，如: 2025-2026';
COMMENT ON COLUMN academic_term.term IS '学期: 1-上学期 2-下学期';
COMMENT ON COLUMN academic_term.start_date IS '开始日期';
COMMENT ON COLUMN academic_term.end_date IS '结束日期';
COMMENT ON COLUMN academic_term.status IS '状态: 0-已结束 1-进行中';
COMMENT ON COLUMN academic_term.created_at IS '创建时间';
COMMENT ON COLUMN academic_term.updated_at IS '更新时间';
COMMENT ON COLUMN academic_term.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_term_school ON academic_term(school_id);
CREATE INDEX idx_term_year ON academic_term(year);
CREATE INDEX idx_term_status ON academic_term(status);

-- 年级表
CREATE TABLE IF NOT EXISTS grade (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    grade_name VARCHAR(50) NOT NULL,
    grade_code INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE grade IS '年级表';
COMMENT ON COLUMN grade.id IS '主键ID';
COMMENT ON COLUMN grade.school_id IS '学校ID';
COMMENT ON COLUMN grade.grade_name IS '年级名称，如: 高一、高二';
COMMENT ON COLUMN grade.grade_code IS '年级编码，用于排序';
COMMENT ON COLUMN grade.created_at IS '创建时间';
COMMENT ON COLUMN grade.updated_at IS '更新时间';
COMMENT ON COLUMN grade.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_grade_school ON grade(school_id);
CREATE INDEX idx_grade_code ON grade(grade_code);

-- 班级表
CREATE TABLE IF NOT EXISTS class (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    grade_id BIGINT NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    class_code INT NOT NULL,
    term_id BIGINT NOT NULL,
    head_teacher_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE class IS '班级表';
COMMENT ON COLUMN class.id IS '主键ID';
COMMENT ON COLUMN class.school_id IS '学校ID';
COMMENT ON COLUMN class.grade_id IS '年级ID';
COMMENT ON COLUMN class.class_name IS '班级名称';
COMMENT ON COLUMN class.class_code IS '班级编码';
COMMENT ON COLUMN class.term_id IS '学期ID';
COMMENT ON COLUMN class.head_teacher_id IS '班主任ID';
COMMENT ON COLUMN class.created_at IS '创建时间';
COMMENT ON COLUMN class.updated_at IS '更新时间';
COMMENT ON COLUMN class.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_class_school ON class(school_id);
CREATE INDEX idx_class_grade ON class(grade_id);
CREATE INDEX idx_class_term ON class(term_id);
CREATE INDEX idx_class_teacher ON class(head_teacher_id);

-- 学生表
CREATE TABLE IF NOT EXISTS student (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE,
    student_no VARCHAR(30) UNIQUE NOT NULL,
    school_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender SMALLINT,
    birth_date DATE,
    enrollment_date DATE,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE student IS '学生表';
COMMENT ON COLUMN student.id IS '主键ID';
COMMENT ON COLUMN student.user_id IS '关联用户表ID';
COMMENT ON COLUMN student.student_no IS '学号';
COMMENT ON COLUMN student.school_id IS '学校ID';
COMMENT ON COLUMN student.name IS '学生姓名';
COMMENT ON COLUMN student.gender IS '性别: 1-男 2-女';
COMMENT ON COLUMN student.birth_date IS '出生日期';
COMMENT ON COLUMN student.enrollment_date IS '入学日期';
COMMENT ON COLUMN student.status IS '状态: 0-离校 1-在读 2-休学';
COMMENT ON COLUMN student.created_at IS '创建时间';
COMMENT ON COLUMN student.updated_at IS '更新时间';
COMMENT ON COLUMN student.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_student_no ON student(student_no);
CREATE INDEX idx_student_school ON student(school_id);
CREATE INDEX idx_student_user ON student(user_id);

-- 班级学生关联表
CREATE TABLE IF NOT EXISTS class_student (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE class_student IS '班级学生关联表';
COMMENT ON COLUMN class_student.id IS '主键ID';
COMMENT ON COLUMN class_student.class_id IS '班级ID';
COMMENT ON COLUMN class_student.student_id IS '学生ID';
COMMENT ON COLUMN class_student.created_at IS '创建时间';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_class_student ON class_student(class_id, student_id);
CREATE INDEX idx_cs_student ON class_student(student_id);

-- 教师表
CREATE TABLE IF NOT EXISTS teacher (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE,
    teacher_no VARCHAR(30) UNIQUE NOT NULL,
    school_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender SMALLINT,
    subject_id BIGINT,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE teacher IS '教师表';
COMMENT ON COLUMN teacher.id IS '主键ID';
COMMENT ON COLUMN teacher.user_id IS '关联用户表ID';
COMMENT ON COLUMN teacher.teacher_no IS '工号';
COMMENT ON COLUMN teacher.school_id IS '学校ID';
COMMENT ON COLUMN teacher.name IS '教师姓名';
COMMENT ON COLUMN teacher.gender IS '性别: 1-男 2-女';
COMMENT ON COLUMN teacher.subject_id IS '主教科目ID';
COMMENT ON COLUMN teacher.status IS '状态: 0-离职 1-在职';
COMMENT ON COLUMN teacher.created_at IS '创建时间';
COMMENT ON COLUMN teacher.updated_at IS '更新时间';
COMMENT ON COLUMN teacher.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_teacher_no ON teacher(teacher_no);
CREATE INDEX idx_teacher_school ON teacher(school_id);
CREATE INDEX idx_teacher_user ON teacher(user_id);

-- 教师班级关联表
CREATE TABLE IF NOT EXISTS teacher_class (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE teacher_class IS '教师班级关联表';
COMMENT ON COLUMN teacher_class.id IS '主键ID';
COMMENT ON COLUMN teacher_class.teacher_id IS '教师ID';
COMMENT ON COLUMN teacher_class.class_id IS '班级ID';
COMMENT ON COLUMN teacher_class.subject_id IS '科目ID';
COMMENT ON COLUMN teacher_class.created_at IS '创建时间';

-- 创建唯一索引
CREATE UNIQUE INDEX uk_teacher_class_subject ON teacher_class(teacher_id, class_id, subject_id);
CREATE INDEX idx_tc_class ON teacher_class(class_id);

-- 科目表
CREATE TABLE IF NOT EXISTS subject (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    subject_name VARCHAR(50) NOT NULL,
    subject_code VARCHAR(20),
    full_score DECIMAL(5,1) DEFAULT 100,
    pass_score DECIMAL(5,1) DEFAULT 60,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

COMMENT ON TABLE subject IS '科目表';
COMMENT ON COLUMN subject.id IS '主键ID';
COMMENT ON COLUMN subject.school_id IS '学校ID';
COMMENT ON COLUMN subject.subject_name IS '科目名称';
COMMENT ON COLUMN subject.subject_code IS '科目编码';
COMMENT ON COLUMN subject.full_score IS '满分';
COMMENT ON COLUMN subject.pass_score IS '及格分';
COMMENT ON COLUMN subject.created_at IS '创建时间';
COMMENT ON COLUMN subject.updated_at IS '更新时间';
COMMENT ON COLUMN subject.deleted IS '删除标记';

-- 创建索引
CREATE INDEX idx_subject_school ON subject(school_id);
CREATE INDEX idx_subject_code ON subject(subject_code);
