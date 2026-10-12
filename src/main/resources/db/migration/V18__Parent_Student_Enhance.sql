-- ============================================
-- V18: 家长学生关联表增强（计划 1.3 家长角色）
-- ponytail: parent_student 建于 V4，只有 created_at、没有 deleted/updated_at；
-- 实体改为继承 BaseEntity（@TableLogic deleted + 自动填充 updated_at）需要补这两列。
-- 全部幂等：IF NOT EXISTS / DROP IF EXISTS，可重复执行。
-- ============================================

ALTER TABLE parent_student ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
ALTER TABLE parent_student ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN parent_student.deleted IS '删除标记: 0-未删除 1-已删除';
COMMENT ON COLUMN parent_student.updated_at IS '更新时间';
COMMENT ON COLUMN parent_student.relationship IS '关系: 取字典 relationship 的 dict_value（father/mother/other）';

-- ponytail: 解绑是软删（deleted=1），V4 的全表唯一键会让同一家长再次绑定同一学生撞键；
-- 改为仅对未删除行生效的部分唯一索引（写法同 V14 的 uk_exam_subject）。
DROP INDEX IF EXISTS uk_parent_student;
CREATE UNIQUE INDEX IF NOT EXISTS uk_parent_student
    ON parent_student (parent_user_id, student_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_parent_student_parent ON parent_student (parent_user_id);

-- V4 的 idx_ps_student 与 idx_parent_student_student 同列，重命名而非重复建两份
DROP INDEX IF EXISTS idx_ps_student;
CREATE INDEX IF NOT EXISTS idx_parent_student_student ON parent_student (student_id);
