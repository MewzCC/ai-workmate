CREATE TABLE IF NOT EXISTS tenant_configuration (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES tenant(id) ON DELETE CASCADE,
    tenant_name VARCHAR(120) NOT NULL,
    tenant_short_name VARCHAR(40),
    locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    fiscal_year_start_month SMALLINT NOT NULL DEFAULT 1,
    approval_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    attendance_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    asset_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    meeting_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    visitor_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    seal_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_approval_days SMALLINT NOT NULL DEFAULT 3,
    expense_currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    password_min_length SMALLINT NOT NULL DEFAULT 8,
    session_timeout_minutes INTEGER NOT NULL DEFAULT 120,
    version INTEGER NOT NULL DEFAULT 0,
    updated_by BIGINT REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenant_config_locale CHECK (locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ck_tenant_config_fiscal_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    CONSTRAINT ck_tenant_config_approval_days CHECK (default_approval_days BETWEEN 1 AND 30),
    CONSTRAINT ck_tenant_config_currency CHECK (expense_currency IN ('CNY', 'USD', 'EUR', 'HKD')),
    CONSTRAINT ck_tenant_config_password_length CHECK (password_min_length BETWEEN 8 AND 32),
    CONSTRAINT ck_tenant_config_session_timeout CHECK (session_timeout_minutes BETWEEN 15 AND 1440)
);

CREATE INDEX IF NOT EXISTS idx_tenant_configuration_updated
    ON tenant_configuration(tenant_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS tenant_configuration_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    category VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    tenant_name VARCHAR(120) NOT NULL,
    tenant_short_name VARCHAR(40),
    locale VARCHAR(16) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    fiscal_year_start_month SMALLINT NOT NULL,
    approval_enabled BOOLEAN NOT NULL,
    attendance_enabled BOOLEAN NOT NULL,
    asset_enabled BOOLEAN NOT NULL,
    meeting_enabled BOOLEAN NOT NULL,
    visitor_enabled BOOLEAN NOT NULL,
    seal_enabled BOOLEAN NOT NULL,
    default_approval_days SMALLINT NOT NULL,
    expense_currency VARCHAR(8) NOT NULL,
    password_min_length SMALLINT NOT NULL,
    session_timeout_minutes INTEGER NOT NULL,
    changed_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenant_config_history_category CHECK (category IN ('PROFILE', 'FEATURES', 'BUSINESS', 'SECURITY')),
    CONSTRAINT uk_tenant_config_history_version UNIQUE (tenant_id, version)
);

CREATE INDEX IF NOT EXISTS idx_tenant_config_history_list
    ON tenant_configuration_history(tenant_id, created_at DESC, id DESC);

INSERT INTO tenant_configuration(tenant_id, tenant_name, updated_by)
SELECT tenant.id, tenant.name,
       (SELECT app_user.id FROM app_user WHERE app_user.tenant_id = tenant.id ORDER BY app_user.id LIMIT 1)
FROM tenant
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'tenant:config:manage', '管理租户配置', '系统设置', '维护当前租户资料、功能开关、业务参数和安全摘要', tenant.id
FROM tenant
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'tenant:config:manage', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SYSTEM_ADMIN', 'SUPER_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'TENANT_CONFIG', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'tenant-config';
