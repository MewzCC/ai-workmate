-- ============================================================
-- 通用审批提交：approval_application 申请单 + 通用流程定义种子
-- ------------------------------------------------------------
-- 「发起审批」模板中心的通用提交接口（POST /api/approval-applications）
-- 落地存储：任意启用表单按 schema_json 校验后提交，绑定启用流程并
-- 创建 workflow_instance / workflow_task 首个待办。
-- JSON 字段沿用 TEXT 存储约定，由应用层校验 JSON 合法性。
-- 全部幂等，首次执行与重复执行均安全。
-- ============================================================

-- ---------- 1. 通用审批申请单 ----------
CREATE TABLE IF NOT EXISTS approval_application (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    applicant_user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    form_id              BIGINT NOT NULL REFERENCES approval_form(id) ON DELETE RESTRICT,
    process_id           BIGINT NOT NULL REFERENCES approval_process(id) ON DELETE RESTRICT,
    form_key             VARCHAR(64) NOT NULL,
    form_name            VARCHAR(120) NOT NULL,
    title                VARCHAR(200) NOT NULL,
    data_json            TEXT NOT NULL DEFAULT '{}',
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    workflow_instance_id BIGINT REFERENCES workflow_instance(id) ON DELETE RESTRICT,
    version              INTEGER NOT NULL DEFAULT 0,
    submitted_at         TIMESTAMP,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_approval_application_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_approval_application_applicant_status
    ON approval_application(tenant_id, applicant_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_approval_application_form
    ON approval_application(tenant_id, form_id, created_at DESC);

-- ---------- 2. 通用表单审批流程定义（供 workflow_instance.definition_id 引用） ----------
INSERT INTO workflow_definition(tenant_id, code, name, business_type, version)
SELECT t.id, 'GENERIC_FORM_APPROVAL', '通用表单审批', 'GENERIC_APPROVAL', 1
FROM tenant t
ON CONFLICT (tenant_id, code, version) DO UPDATE SET
    name = EXCLUDED.name,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;
