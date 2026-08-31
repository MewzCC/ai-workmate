-- 通用审批重新提交需要为同一业务保留多个历史流程实例，但任一时刻只能有一个运行中实例。
ALTER TABLE workflow_instance
    DROP CONSTRAINT IF EXISTS workflow_instance_tenant_id_business_type_business_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_instance_running_business
    ON workflow_instance(tenant_id, business_type, business_id)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_workflow_instance_business_history
    ON workflow_instance(tenant_id, business_type, business_id, created_at, id);
