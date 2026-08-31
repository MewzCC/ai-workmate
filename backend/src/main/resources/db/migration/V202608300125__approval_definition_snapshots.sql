-- 通用审批在提交时冻结表单、流程和规则配置，避免历史申请受后续配置变更影响。
ALTER TABLE approval_application
    ADD COLUMN IF NOT EXISTS form_schema_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS form_version_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS process_node_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS process_version_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS rule_snapshot TEXT;

ALTER TABLE approval_application
    DROP CONSTRAINT IF EXISTS ck_approval_application_snapshot_versions;

ALTER TABLE approval_application
    ADD CONSTRAINT ck_approval_application_snapshot_versions CHECK (
        (form_version_snapshot IS NULL OR form_version_snapshot >= 0)
        AND (process_version_snapshot IS NULL OR process_version_snapshot >= 0)
    );
