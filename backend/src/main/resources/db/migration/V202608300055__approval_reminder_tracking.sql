-- 审批催办与时效跟踪：到期时间沿用 due_at，新增催办次数和最后催办时间。
ALTER TABLE workflow_task
    ADD COLUMN IF NOT EXISTS reminder_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMP;

ALTER TABLE workflow_task
    DROP CONSTRAINT IF EXISTS ck_workflow_task_reminder_count;
ALTER TABLE workflow_task
    ADD CONSTRAINT ck_workflow_task_reminder_count CHECK (reminder_count >= 0);

CREATE INDEX IF NOT EXISTS idx_workflow_task_pending_due
    ON workflow_task(tenant_id, due_at)
    WHERE status = 'PENDING' AND due_at IS NOT NULL;
