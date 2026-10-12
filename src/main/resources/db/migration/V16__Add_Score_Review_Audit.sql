-- 契约 K2：成绩审核留痕。
-- 此前驳回原因以「【驳回】」前缀借用 remark 列暂存，审核人只能从 recorder_id 猜。
-- 现给 score 加独立的 reviewer_id / reject_reason 两列；历史 remark 里的「【驳回】」内容留原样不迁移。
ALTER TABLE score ADD COLUMN IF NOT EXISTS reviewer_id BIGINT;
ALTER TABLE score ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);

COMMENT ON COLUMN score.reviewer_id IS '审核人ID → sys_user.id（通过/驳回时写入）';
COMMENT ON COLUMN score.reject_reason IS '驳回原因（status=2 时写入，重新通过时清空）';
