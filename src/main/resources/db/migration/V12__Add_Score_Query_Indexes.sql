-- score 表此前除主键外无索引，72 万行查询全部走 seq scan。
-- 列顺序对应 selectByExamId 的 WHERE：exam_id 必填，school_id/class_id/subject_id 可选。
-- deleted 放在 WHERE 而不是索引列里：软删行占比极低，部分索引更小且能覆盖全部调用点。
CREATE INDEX IF NOT EXISTS idx_score_exam_school_class_subject
    ON score (exam_id, school_id, class_id, subject_id)
    WHERE deleted = 0;

-- 学生跨考试查询（成绩单、趋势分析）走这条。
CREATE INDEX IF NOT EXISTS idx_score_student_exam
    ON score (student_id, exam_id)
    WHERE deleted = 0;

-- 唯一性校验 selectByExamStudentSubject 的精确定位。
CREATE INDEX IF NOT EXISTS idx_score_exam_student_subject
    ON score (exam_id, student_id, subject_id)
    WHERE deleted = 0;
