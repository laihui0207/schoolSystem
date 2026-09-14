-- 删除被 V12 复合索引/部分索引完全覆盖、或永远不被查询计划选中的冗余索引。
-- 保留理由见注释；删除决策基于 pg_stat_user_indexes.idx_scan 及 EXPLAIN 验证。

-- idx_score_school_exam (school_id, exam_id) — 74 次 scan
-- 完全被 idx_score_exam_school_class_subject(exam_id, school_id, ...) WHERE deleted=0 前缀覆盖。
-- 所有 selectByExamId 调用均带 deleted=0，planner 会选更小的部分索引。
DROP INDEX IF EXISTS idx_score_school_exam;

-- idx_score_student — 27 MB，3 次 scan
-- 被 idx_score_student_exam(student_id, exam_id) WHERE deleted=0 覆盖（学生查询均带 deleted=0）。
DROP INDEX IF EXISTS idx_score_student;

-- idx_score_status — 23 MB，0 次 scan
-- status 基数极低（0/1/2），索引选择率差，planner 始终走 seq scan 而非此索引。
DROP INDEX IF EXISTS idx_score_status;
