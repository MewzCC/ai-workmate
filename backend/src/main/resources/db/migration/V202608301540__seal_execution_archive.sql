ALTER TABLE seal_usage
    DROP CONSTRAINT IF EXISTS ck_seal_status;

ALTER TABLE seal_usage
    ADD COLUMN IF NOT EXISTS actual_copies INTEGER,
    ADD COLUMN IF NOT EXISTS handler_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS returned_at TIMESTAMP;

ALTER TABLE seal_usage
    ADD CONSTRAINT ck_seal_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'USED', 'RETURNED')
    ),
    ADD CONSTRAINT ck_seal_actual_copies CHECK (actual_copies IS NULL OR actual_copies > 0);

CREATE INDEX IF NOT EXISTS idx_seal_execution_status
    ON seal_usage (tenant_id, status, used_at DESC)
    WHERE status IN ('APPROVED', 'USED');

CREATE TABLE IF NOT EXISTS seal_usage_document (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    seal_usage_id       BIGINT NOT NULL REFERENCES seal_usage(id) ON DELETE CASCADE,
    display_name        VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    mime_type           VARCHAR(120) NOT NULL,
    file_size           BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_seal_document_size CHECK (file_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_seal_document_usage
    ON seal_usage_document (tenant_id, seal_usage_id, created_at DESC);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT v.code, v.name, '行政资产', v.description, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('seal:register', '登记本人用印与归还', '登记本人申请的实际用印、归还和受控文件'),
    ('seal:register:any', '登记全部用印与归还', '登记租户内全部实际用印、归还和受控文件')
) AS v(code, name, description)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'seal:register', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'seal:register:any', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN')
ON CONFLICT DO NOTHING;
