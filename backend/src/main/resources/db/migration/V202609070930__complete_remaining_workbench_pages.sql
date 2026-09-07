CREATE TABLE IF NOT EXISTS workbench_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    module_key VARCHAR(60) NOT NULL,
    record_code VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    category VARCHAR(80),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    amount NUMERIC(18, 2),
    owner VARCHAR(100),
    details VARCHAR(4000),
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workbench_record_module CHECK (module_key IN (
        'expense', 'budget', 'contracts', 'suppliers',
        'api-center', 'page-actions', 'runtime-logs', 'sandbox-replay',
        'data-permission', 'ai-permission', 'tenant-config', 'dictionary'
    )),
    CONSTRAINT ck_workbench_record_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'PENDING', 'COMPLETED', 'DISABLED', 'FAILED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workbench_record_code
    ON workbench_record(tenant_id, module_key, record_code)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_workbench_record_list
    ON workbench_record(tenant_id, module_key, status, updated_at DESC)
    WHERE deleted = FALSE;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, permission.module, permission.description, tenant.id
FROM tenant
CROSS JOIN (
    VALUES
        ('workbench:finance:manage', '管理财务合同台账', '财务合同', '维护费用、预算、合同与供应商台账'),
        ('workbench:integration:manage', '管理开放平台台账', '开放平台', '维护接口、页面动作、运行日志与沙箱回放记录'),
        ('workbench:settings:manage', '管理系统配置台账', '系统设置', '维护数据权限、AI 权限、租户与数据字典配置')
) AS permission(code, name, module, description)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT assignment.role_code, assignment.permission_code, tenant.id
FROM tenant
CROSS JOIN (
    VALUES
        ('FINANCE_ADMIN', 'workbench:finance:manage'),
        ('SYSTEM_ADMIN', 'workbench:integration:manage'),
        ('SYSTEM_ADMIN', 'workbench:settings:manage')
) AS assignment(role_code, permission_code)
WHERE EXISTS (
    SELECT 1 FROM rbac_role role
    WHERE role.code = assignment.role_code AND role.tenant_id = tenant.id
)
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT 'SUPER_ADMIN', permission.code, permission.tenant_id
FROM rbac_permission permission
WHERE permission.code IN (
    'workbench:finance:manage', 'workbench:integration:manage', 'workbench:settings:manage'
)
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'WORKBENCH_MODULE', updated_at = CURRENT_TIMESTAMP
WHERE route_key IN (
    'expense', 'budget', 'contracts', 'suppliers',
    'api-center', 'page-actions', 'runtime-logs', 'sandbox-replay',
    'data-permission', 'ai-permission', 'tenant-config', 'dictionary'
);
