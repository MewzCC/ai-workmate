CREATE TABLE IF NOT EXISTS business_contract (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    contract_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contract_type VARCHAR(24) NOT NULL,
    counterparty_name VARCHAR(160) NOT NULL,
    supplier_id BIGINT REFERENCES supplier(id),
    supplier_label VARCHAR(160),
    owner_user_id BIGINT NOT NULL REFERENCES app_user(id),
    owner_label VARCHAR(160) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    paid_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    signed_date DATE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    fulfillment_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    summary VARCHAR(2000),
    reminder_count INTEGER NOT NULL DEFAULT 0,
    last_reminded_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_contract_type CHECK (contract_type IN ('PURCHASE', 'SALES', 'SERVICE', 'LEASE', 'OTHER')),
    CONSTRAINT ck_contract_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'TERMINATED')),
    CONSTRAINT ck_contract_fulfillment CHECK (fulfillment_status IN ('NOT_STARTED', 'IN_PROGRESS', 'FULFILLED', 'BREACHED')),
    CONSTRAINT ck_contract_currency CHECK (currency IN ('CNY', 'USD', 'EUR', 'HKD')),
    CONSTRAINT ck_contract_amount CHECK (amount > 0 AND paid_amount >= 0 AND paid_amount <= amount),
    CONSTRAINT ck_contract_dates CHECK (start_date <= end_date AND (signed_date IS NULL OR signed_date <= start_date)),
    CONSTRAINT ck_contract_reminders CHECK (reminder_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_contract_code
    ON business_contract(tenant_id, contract_code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_business_contract_list
    ON business_contract(tenant_id, status, end_date, updated_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_business_contract_owner
    ON business_contract(tenant_id, owner_user_id, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_business_contract_supplier
    ON business_contract(tenant_id, supplier_id) WHERE supplier_id IS NOT NULL AND deleted = FALSE;

CREATE TABLE IF NOT EXISTS contract_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    contract_id BIGINT NOT NULL REFERENCES business_contract(id),
    event_type VARCHAR(32) NOT NULL,
    from_value VARCHAR(100),
    to_value VARCHAR(100),
    amount NUMERIC(18, 2),
    detail VARCHAR(1000),
    operator_id BIGINT NOT NULL REFERENCES app_user(id),
    operator_label VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_contract_event_type CHECK (event_type IN (
        'CREATED', 'UPDATED', 'STATUS_CHANGED', 'FULFILLMENT_CHANGED', 'PAYMENT_RECORDED', 'EXPIRY_REMINDER'
    )),
    CONSTRAINT ck_contract_event_amount CHECK (amount IS NULL OR amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_contract_event_timeline
    ON contract_event(tenant_id, contract_id, created_at DESC);

INSERT INTO business_contract(
    tenant_id, contract_code, name, contract_type, counterparty_name,
    owner_user_id, owner_label, amount, paid_amount, currency,
    signed_date, start_date, end_date, status, fulfillment_status, summary,
    version, deleted, created_by, updated_by, created_at, updated_at
)
SELECT record.tenant_id,
       UPPER(record.record_code),
       record.title,
       CASE UPPER(COALESCE(record.category, ''))
           WHEN 'PURCHASE' THEN 'PURCHASE'
           WHEN 'SALES' THEN 'SALES'
           WHEN 'SERVICE' THEN 'SERVICE'
           WHEN 'LEASE' THEN 'LEASE'
           ELSE 'OTHER'
       END,
       COALESCE(NULLIF(record.owner, ''), '未配置相对方'),
       record.created_by,
       COALESCE(NULLIF(user_account.display_name, ''), NULLIF(user_account.email, ''), user_account.username),
       GREATEST(COALESCE(record.amount, 0.01), 0.01),
       0,
       'CNY',
       record.created_at::DATE,
       record.created_at::DATE,
       (record.created_at::DATE + INTERVAL '1 year')::DATE,
       CASE record.status
           WHEN 'ACTIVE' THEN 'ACTIVE'
           WHEN 'COMPLETED' THEN 'COMPLETED'
           WHEN 'DISABLED' THEN 'TERMINATED'
           WHEN 'FAILED' THEN 'TERMINATED'
           ELSE 'DRAFT'
       END,
       CASE WHEN record.status = 'COMPLETED' THEN 'FULFILLED' ELSE 'NOT_STARTED' END,
       LEFT(record.details, 2000),
       record.version,
       FALSE,
       record.created_by,
       record.updated_by,
       record.created_at,
       record.updated_at
FROM workbench_record record
JOIN app_user user_account ON user_account.id = record.created_by
WHERE record.module_key = 'contracts' AND record.deleted = FALSE
ON CONFLICT DO NOTHING;

INSERT INTO contract_event(
    tenant_id, contract_id, event_type, from_value, to_value, operator_id, operator_label, created_at
)
SELECT contract.tenant_id, contract.id, 'CREATED', NULL, contract.status, contract.updated_by,
       COALESCE(NULLIF(user_account.display_name, ''), NULLIF(user_account.email, ''), user_account.username),
       contract.updated_at
FROM business_contract contract
JOIN app_user user_account ON user_account.id = contract.updated_by
WHERE NOT EXISTS (
    SELECT 1 FROM contract_event event
    WHERE event.tenant_id = contract.tenant_id AND event.contract_id = contract.id
);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'contract:manage', '管理合同', '财务合同', '维护合同档案、履约状态、付款记录与到期催办', tenant.id
FROM tenant
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT role.tenant_id, role.code, 'contract:manage'
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'FINANCE_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'CONTRACT', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'contracts';
