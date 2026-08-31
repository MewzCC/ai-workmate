-- 前后加签：WAITING 表示已编排但尚未轮到执行的待办，任一业务仍只有一个 PENDING 待办。
ALTER TABLE workflow_task
    ADD COLUMN IF NOT EXISTS parent_task_id BIGINT REFERENCES workflow_task(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS add_sign_mode VARCHAR(8);

ALTER TABLE workflow_task
    DROP CONSTRAINT IF EXISTS ck_workflow_task_status;
ALTER TABLE workflow_task
    ADD CONSTRAINT ck_workflow_task_status CHECK (
        status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    );

ALTER TABLE workflow_task
    DROP CONSTRAINT IF EXISTS ck_workflow_task_add_sign_mode;
ALTER TABLE workflow_task
    ADD CONSTRAINT ck_workflow_task_add_sign_mode CHECK (
        add_sign_mode IS NULL OR add_sign_mode IN ('PRE', 'POST')
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_task_waiting_child
    ON workflow_task(tenant_id, parent_task_id)
    WHERE status = 'WAITING' AND parent_task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_workflow_task_parent
    ON workflow_task(tenant_id, parent_task_id, status);
