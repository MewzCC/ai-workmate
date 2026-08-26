CREATE TABLE IF NOT EXISTS agent_tenant_policy (
    tenant_id                       BIGINT PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    enabled                         BOOLEAN NOT NULL DEFAULT FALSE,
    write_tools_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    max_plan_steps                  INTEGER,
    max_tool_calls                  INTEGER,
    max_concurrent_tasks_per_user   INTEGER,
    max_query_size                  INTEGER,
    max_tool_timeout_ms             INTEGER,
    max_task_timeout_ms             INTEGER,
    created_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_tenant_plan_steps CHECK (max_plan_steps IS NULL OR max_plan_steps BETWEEN 1 AND 3),
    CONSTRAINT chk_agent_tenant_tool_calls CHECK (max_tool_calls IS NULL OR max_tool_calls BETWEEN 1 AND 5),
    CONSTRAINT chk_agent_tenant_concurrency CHECK (max_concurrent_tasks_per_user IS NULL OR max_concurrent_tasks_per_user BETWEEN 1 AND 2),
    CONSTRAINT chk_agent_tenant_query_size CHECK (max_query_size IS NULL OR max_query_size BETWEEN 1 AND 50),
    CONSTRAINT chk_agent_tenant_tool_timeout CHECK (max_tool_timeout_ms IS NULL OR max_tool_timeout_ms BETWEEN 1000 AND 30000),
    CONSTRAINT chk_agent_tenant_task_timeout CHECK (max_task_timeout_ms IS NULL OR max_task_timeout_ms BETWEEN 1000 AND 120000)
);

CREATE TABLE IF NOT EXISTS agent_tool (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT REFERENCES tenant(id) ON DELETE CASCADE,
    code                     VARCHAR(80) NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    description              VARCHAR(500) NOT NULL,
    handler_version          VARCHAR(40) NOT NULL,
    parameters_schema        JSONB NOT NULL,
    output_schema            JSONB NOT NULL,
    schema_hash              VARCHAR(80) NOT NULL,
    risk_level               VARCHAR(8) NOT NULL,
    required_permissions     JSONB NOT NULL,
    permission_mode          VARCHAR(8) NOT NULL,
    data_scope_policy        VARCHAR(40) NOT NULL,
    retry_policy             VARCHAR(32) NOT NULL,
    side_effect              VARCHAR(20) NOT NULL,
    confirmation_policy      VARCHAR(20) NOT NULL,
    max_result_items         INTEGER NOT NULL,
    max_result_bytes         INTEGER NOT NULL,
    timeout_ms               INTEGER NOT NULL,
    audit_level              VARCHAR(20) NOT NULL,
    enabled                  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_tool_code CHECK (code ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$'),
    CONSTRAINT chk_agent_tool_risk CHECK (risk_level IN ('L0', 'L1', 'L2')),
    CONSTRAINT chk_agent_tool_permission_mode CHECK (permission_mode IN ('ALL', 'ANY')),
    CONSTRAINT chk_agent_tool_scope CHECK (data_scope_policy IN ('SELF', 'ASSIGNED_TO_SELF', 'TENANT_SCOPED', 'FIXED_RESOURCE')),
    CONSTRAINT chk_agent_tool_retry CHECK (retry_policy IN ('READ_ONLY_SAFE', 'BUSINESS_IDEMPOTENT', 'NEVER')),
    CONSTRAINT chk_agent_tool_side_effect CHECK (side_effect IN ('NONE', 'SINGLE_WRITE')),
    CONSTRAINT chk_agent_tool_confirmation CHECK (confirmation_policy IN ('NONE', 'EXPLICIT', 'SECONDARY')),
    CONSTRAINT chk_agent_tool_permissions CHECK (jsonb_typeof(required_permissions) = 'array' AND jsonb_array_length(required_permissions) > 0),
    CONSTRAINT chk_agent_tool_result_items CHECK (max_result_items BETWEEN 1 AND 50),
    CONSTRAINT chk_agent_tool_result_bytes CHECK (max_result_bytes BETWEEN 1024 AND 262144),
    CONSTRAINT chk_agent_tool_timeout CHECK (timeout_ms BETWEEN 1000 AND 30000)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool_platform_code
    ON agent_tool(code) WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool_tenant_code
    ON agent_tool(tenant_id, code) WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agent_tool_tenant_enabled
    ON agent_tool(tenant_id, enabled, code);

INSERT INTO agent_tenant_policy (tenant_id, enabled, write_tools_enabled)
SELECT id, FALSE, FALSE FROM tenant
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO rbac_permission (code, name, module, description, tenant_id)
SELECT 'knowledge:search', '检索授权知识库', 'AI 能力', '允许 Agent 检索当前用户有权访问的知识库', id
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_permission (code, name, module, description, tenant_id)
SELECT 'notification:read:self', '查看本人通知', 'AI 能力', '允许 Agent 查询当前用户自己的站内通知', id
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission (tenant_id, role_code, permission_code)
SELECT DISTINCT tenant_id, role_code, 'knowledge:search'
FROM rbac_role_permission
WHERE permission_code = 'route:knowledge-base'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission (tenant_id, role_code, permission_code)
SELECT DISTINCT tenant_id, role_code, 'notification:read:self'
FROM rbac_role_permission
WHERE permission_code = 'messages:read'
ON CONFLICT DO NOTHING;
