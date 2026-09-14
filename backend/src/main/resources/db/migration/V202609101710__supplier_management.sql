CREATE TABLE IF NOT EXISTS supplier (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    supplier_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    short_name VARCHAR(80),
    unified_social_credit_code VARCHAR(18),
    category VARCHAR(24) NOT NULL,
    supplier_level VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    contact_name VARCHAR(80),
    contact_phone VARCHAR(32),
    contact_email VARCHAR(160),
    address VARCHAR(300),
    payment_terms VARCHAR(120),
    risk_note VARCHAR(1000),
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_category CHECK (category IN ('MATERIAL', 'SERVICE', 'LOGISTICS', 'CONSULTING', 'OTHER')),
    CONSTRAINT ck_supplier_level CHECK (supplier_level IN ('STRATEGIC', 'PREFERRED', 'STANDARD', 'RESTRICTED')),
    CONSTRAINT ck_supplier_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'BLACKLISTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supplier_code
    ON supplier(tenant_id, supplier_code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_supplier_credit_code
    ON supplier(tenant_id, unified_social_credit_code)
    WHERE deleted = FALSE AND unified_social_credit_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_supplier_list
    ON supplier(tenant_id, status, category, updated_at DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS supplier_status_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    reason VARCHAR(500),
    operator_id BIGINT NOT NULL REFERENCES app_user(id),
    operator_label VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_history_from_status CHECK (
        from_status IS NULL OR from_status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'BLACKLISTED')
    ),
    CONSTRAINT ck_supplier_history_to_status CHECK (
        to_status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'BLACKLISTED')
    )
);

CREATE INDEX IF NOT EXISTS idx_supplier_status_history
    ON supplier_status_history(tenant_id, supplier_id, created_at DESC);

INSERT INTO supplier(
    tenant_id, supplier_code, name, short_name, category, supplier_level, status,
    contact_name, risk_note, version, deleted, created_by, updated_by, created_at, updated_at
)
SELECT record.tenant_id,
       UPPER(record.record_code),
       record.title,
       record.title,
       CASE UPPER(COALESCE(record.category, ''))
           WHEN 'MATERIAL' THEN 'MATERIAL'
           WHEN 'SERVICE' THEN 'SERVICE'
           WHEN 'LOGISTICS' THEN 'LOGISTICS'
           WHEN 'CONSULTING' THEN 'CONSULTING'
           ELSE 'OTHER'
       END,
       'STANDARD',
       CASE record.status
           WHEN 'ACTIVE' THEN 'ACTIVE'
           WHEN 'COMPLETED' THEN 'ACTIVE'
           WHEN 'DISABLED' THEN 'SUSPENDED'
           WHEN 'FAILED' THEN 'SUSPENDED'
           ELSE 'DRAFT'
       END,
       record.owner,
       LEFT(record.details, 1000),
       record.version,
       FALSE,
       record.created_by,
       record.updated_by,
       record.created_at,
       record.updated_at
FROM workbench_record record
WHERE record.module_key = 'suppliers' AND record.deleted = FALSE
ON CONFLICT DO NOTHING;

INSERT INTO supplier_status_history(
    tenant_id, supplier_id, from_status, to_status, reason,
    operator_id, operator_label, created_at
)
SELECT supplier.tenant_id,
       supplier.id,
       NULL,
       supplier.status,
       NULL,
       supplier.updated_by,
       COALESCE(NULLIF(user_account.display_name, ''), NULLIF(user_account.email, ''), user_account.username),
       supplier.updated_at
FROM supplier
JOIN app_user user_account ON user_account.id = supplier.updated_by
WHERE NOT EXISTS (
    SELECT 1 FROM supplier_status_history history
    WHERE history.tenant_id = supplier.tenant_id AND history.supplier_id = supplier.id
);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'supplier:manage', '管理供应商', '财务合同', '维护供应商档案并执行启用、暂停与黑名单状态流转', tenant.id
FROM tenant
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT role.tenant_id, role.code, 'supplier:manage'
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'FINANCE_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'SUPPLIER', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'suppliers';
