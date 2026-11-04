-- Per-attempt snapshots prevent historical assignees from reading a later resubmission's payload.
CREATE TABLE IF NOT EXISTS approval_execution (
    instance_id BIGINT PRIMARY KEY REFERENCES workflow_instance(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    application_id BIGINT NOT NULL REFERENCES approval_application(id) ON DELETE RESTRICT,
    applicant_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    form_key VARCHAR(64) NOT NULL,
    form_name VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    data_json TEXT NOT NULL,
    form_schema TEXT,
    form_version INTEGER,
    process_nodes TEXT,
    process_version INTEGER,
    rules TEXT,
    plan_json TEXT,
    current_node_index INTEGER NOT NULL DEFAULT 0 CHECK(current_node_index BETWEEN 0 AND 50),
    version INTEGER NOT NULL DEFAULT 0 CHECK(version >= 0),
    submitted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_approval_execution_business ON approval_execution(tenant_id,application_id,instance_id);
ALTER TABLE workflow_task ADD COLUMN IF NOT EXISTS process_node_index INTEGER,
    ADD COLUMN IF NOT EXISTS process_node_name VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS ux_generic_task_execution_node ON workflow_task(tenant_id,instance_id,process_node_index)
    WHERE business_type='GENERIC_APPROVAL' AND process_node_index IS NOT NULL;

-- Preserve only the attempt that existing rows can prove. Never manufacture a resolved legacy plan.
INSERT INTO approval_execution(instance_id,tenant_id,application_id,applicant_user_id,form_key,form_name,title,
    data_json,form_schema,form_version,process_nodes,process_version,rules,submitted_at)
SELECT wi.id,a.tenant_id,a.id,a.applicant_user_id,a.form_key,a.form_name,a.title,
    a.data_json,a.form_schema_snapshot,a.form_version_snapshot,a.process_node_snapshot,a.process_version_snapshot,a.rule_snapshot,a.submitted_at
FROM approval_application a JOIN workflow_instance wi ON wi.id=a.workflow_instance_id AND wi.tenant_id=a.tenant_id
    AND wi.business_type='GENERIC_APPROVAL' AND wi.business_id=a.id AND wi.applicant_id=a.applicant_user_id
ON CONFLICT(instance_id) DO NOTHING;
