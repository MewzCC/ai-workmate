CREATE TABLE IF NOT EXISTS data_dictionary_type (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dictionary_type_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_dictionary_type_sort CHECK (sort_order BETWEEN 0 AND 9999)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dictionary_type_code
    ON data_dictionary_type(tenant_id, code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dictionary_type_list
    ON data_dictionary_type(tenant_id, status, sort_order, updated_at DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS data_dictionary_item (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    dictionary_type_id BIGINT NOT NULL REFERENCES data_dictionary_type(id) ON DELETE RESTRICT,
    item_value VARCHAR(128) NOT NULL,
    label VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dictionary_item_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_dictionary_item_sort CHECK (sort_order BETWEEN 0 AND 9999)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dictionary_item_value
    ON data_dictionary_item(tenant_id, dictionary_type_id, item_value) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_dictionary_item_list
    ON data_dictionary_item(tenant_id, dictionary_type_id, status, sort_order, updated_at DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS data_dictionary_item_usage (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    dictionary_item_id BIGINT NOT NULL REFERENCES data_dictionary_item(id) ON DELETE RESTRICT,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dictionary_item_usage UNIQUE (tenant_id, dictionary_item_id, resource_type, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_dictionary_item_usage_item
    ON data_dictionary_item_usage(tenant_id, dictionary_item_id);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'dictionary:manage', '管理数据字典', '系统设置', '维护字典类型、字典项及启停状态', tenant.id
FROM tenant
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'dictionary:manage', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SYSTEM_ADMIN', 'SUPER_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'DICTIONARY', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'dictionary';
