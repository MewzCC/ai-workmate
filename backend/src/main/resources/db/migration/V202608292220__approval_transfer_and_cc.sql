-- 审批转交与抄送审计字段：显式保存原处理人、目标用户、理由（comment）和操作时间。
ALTER TABLE workflow_action_log
    ADD COLUMN IF NOT EXISTS original_assignee_user_id BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS target_user_id BIGINT REFERENCES app_user(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_workflow_action_target_user
    ON workflow_action_log(tenant_id, target_user_id, created_at DESC)
    WHERE target_user_id IS NOT NULL;
