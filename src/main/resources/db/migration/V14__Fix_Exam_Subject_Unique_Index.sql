-- ponytail: V3 的 uk_exam_subject(exam_id, subject_id) 不含 deleted，而 ExamSubjectMapper.deleteByExamId
-- 是软删（UPDATE deleted=1），软删行仍占着唯一键，编辑考试重插同一科目必撞键。
-- 改为仅对未删除行生效的部分索引（写法参考 V12）。旧索引保证了全表唯一，未删除行天然满足新约束，无需清洗数据。
DROP INDEX IF EXISTS uk_exam_subject;
CREATE UNIQUE INDEX IF NOT EXISTS uk_exam_subject
    ON exam_subject (exam_id, subject_id)
    WHERE deleted = 0;
