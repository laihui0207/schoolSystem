-- 契约 R：总分排名独立存储。
-- rank_in_* 永远是「该行所属科目」的单科名次；total_rank_* 是该学生本场考试的总分名次（同一学生所有行相同）。
-- 此前两套排名共用 rank_in_* 三列，publishExam 的「总分→逐科」调用序列会互相覆盖。
ALTER TABLE score ADD COLUMN IF NOT EXISTS total_rank_in_class INT;
ALTER TABLE score ADD COLUMN IF NOT EXISTS total_rank_in_grade INT;
ALTER TABLE score ADD COLUMN IF NOT EXISTS total_rank_in_school INT;

COMMENT ON COLUMN score.total_rank_in_class IS '总分班级排名（同一学生同场考试所有行相同）';
COMMENT ON COLUMN score.total_rank_in_grade IS '总分年级排名（同一学生同场考试所有行相同）';
COMMENT ON COLUMN score.total_rank_in_school IS '总分校级排名（同一学生同场考试所有行相同）';
