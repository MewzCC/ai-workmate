CREATE TABLE IF NOT EXISTS data_permission_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    scope_type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_permission_policy_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_data_permission_policy_scope CHECK (
        scope_type IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_AND_CHILDREN', 'CUSTOM_DEPARTMENTS', 'ALL')
    )
);

CREATE TABLE IF NOT EXISTS data_permission_policy_department (
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    policy_id BIGINT NOT NULL REFERENCES data_permission_policy(id) ON DELETE CASCADE,
    department_id BIGINT NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    PRIMARY KEY (tenant_id, policy_id, department_id)
);

CREATE TABLE IF NOT EXISTS data_permission_role_binding (
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    role_code VARCHAR(40) NOT NULL REFERENCES rbac_role(code) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES data_permission_policy(id) ON DELETE RESTRICT,
    updated_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, role_code)
);

CREATE TABLE IF NOT EXISTS data_permission_user_exception (
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES data_permission_policy(id) ON DELETE RESTRICT,
    updated_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_data_permission_policy_tenant
    ON data_permission_policy(tenant_id, enabled, id);
CREATE INDEX IF NOT EXISTS idx_data_permission_role_policy
    ON data_permission_role_binding(tenant_id, policy_id);
CREATE INDEX IF NOT EXISTS idx_data_permission_user_policy
    ON data_permission_user_exception(tenant_id, policy_id);

INSERT INTO data_permission_policy(tenant_id, name, description, scope_type, created_by)
SELECT ds.tenant_id,
       ds.role_code || ' 默认范围',
       '由原角色数据范围迁移生成',
       ds.scope_type,
       creator.id
FROM data_scope ds
JOIN LATERAL (
    SELECT u.id FROM app_user u
    WHERE u.tenant_id = ds.tenant_id
    ORDER BY CASE WHEN u.role = 'SUPER_ADMIN' THEN 0 ELSE 1 END, u.id
    LIMIT 1
) creator ON TRUE
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO data_permission_role_binding(tenant_id, role_code, policy_id, updated_by)
SELECT ds.tenant_id, ds.role_code, policy.id, policy.created_by
FROM data_scope ds
JOIN data_permission_policy policy
  ON policy.tenant_id = ds.tenant_id AND policy.name = ds.role_code || ' 默认范围'
ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'data-scope:manage', '管理数据权限策略', '系统设置',
       '配置角色与用户的数据访问范围并预览生效结果', t.id
FROM tenant t
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT r.tenant_id, r.code, 'data-scope:manage'
FROM rbac_role r
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'DATA_PERMISSION', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'data-permission';
