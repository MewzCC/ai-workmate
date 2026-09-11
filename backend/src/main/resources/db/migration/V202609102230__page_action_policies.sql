CREATE TABLE IF NOT EXISTS agent_page_action_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    page_id VARCHAR(80) NOT NULL,
    tool_code VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_page_action_version CHECK (version >= 1),
    CONSTRAINT uk_agent_page_action_policy UNIQUE (tenant_id, page_id, tool_code)
);

CREATE INDEX IF NOT EXISTS idx_agent_page_action_policy_page
    ON agent_page_action_policy(tenant_id, page_id, enabled);

WITH supported(page_id, tool_code) AS (VALUES
    ('dashboard', 'todo.query'),
    ('dashboard', 'notification.mine'),
    ('todo-list', 'todo.query'),
    ('my-applications', 'leave.mine'),
    ('my-applications', 'leave.createDraft'),
    ('my-applications', 'leave.submit'),
    ('my-applications', 'leave.apply'),
    ('knowledge-base', 'knowledge.search'),
    ('message-center', 'notification.mine'),
    ('ai-workspace', 'todo.query'),
    ('ai-workspace', 'notification.mine'),
    ('ai-workspace', 'leave.mine'),
    ('ai-workspace', 'leave.createDraft'),
    ('ai-workspace', 'leave.submit'),
    ('ai-workspace', 'leave.apply'),
    ('ai-workspace', 'knowledge.search')
)
INSERT INTO agent_page_action_policy(
    tenant_id, page_id, tool_code, enabled, version, updated_by, created_at, updated_at
)
SELECT record.tenant_id, supported.page_id, supported.tool_code,
       record.status = 'ACTIVE', GREATEST(1, record.version), record.updated_by,
       record.created_at, record.updated_at
FROM workbench_record record
JOIN supported ON LOWER(record.record_code) = LOWER(supported.page_id || ':' || supported.tool_code)
WHERE record.module_key = 'page-actions' AND record.deleted = FALSE
ON CONFLICT (tenant_id, page_id, tool_code) DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'page-action:manage', '管理页面操作配置', '开放平台',
       '仅允许关闭或恢复代码注册的页面与 Agent 工具绑定，不得增加动态能力', tenant.id
FROM tenant
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT role.tenant_id, role.code, 'page-action:manage'
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'PAGE_ACTIONS', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'page-actions';
