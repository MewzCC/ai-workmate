-- 通用审批草稿生命周期：允许未绑定流程的 DRAFT，提交时再创建工作流。
ALTER TABLE approval_application
    ALTER COLUMN process_id DROP NOT NULL;

ALTER TABLE approval_application
    DROP CONSTRAINT IF EXISTS ck_approval_application_status;

ALTER TABLE approval_application
    ADD CONSTRAINT ck_approval_application_status CHECK (
        status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
    );

CREATE INDEX IF NOT EXISTS idx_approval_application_draft
    ON approval_application(tenant_id, applicant_user_id, updated_at DESC)
    WHERE status = 'DRAFT';
