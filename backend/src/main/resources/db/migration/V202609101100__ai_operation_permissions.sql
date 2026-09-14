INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant
CROSS JOIN (VALUES
    ('agent-permission:manage', '管理 AI 操作权限', '配置租户 Agent 开关、工具开关与角色工具授权'),
    ('agent:tool:todo.query', 'Agent 查询本人待办', '允许 Agent 使用受控待办查询工具'),
    ('agent:tool:leave.mine', 'Agent 查询本人申请', '允许 Agent 使用受控本人申请查询工具'),
    ('agent:tool:knowledge.search', 'Agent 检索授权知识', '允许 Agent 使用受控知识检索工具'),
    ('agent:tool:notification.mine', 'Agent 查询本人通知', '允许 Agent 使用受控本人通知查询工具'),
    ('agent:tool:leave.createDraft', 'Agent 创建请假草稿', '允许 Agent 创建本人请假草稿，仍需确认与实时鉴权'),
    ('agent:tool:leave.submit', 'Agent 提交请假草稿', '允许 Agent 提交本人已有草稿，仍需二次确认'),
    ('agent:tool:leave.apply', 'Agent 原子提交请假', '允许 Agent 原子创建并提交本人请假申请，仍需二次确认')
) AS permission(code, name, description)
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT role.tenant_id, role.code, 'agent-permission:manage'
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

WITH tool_permission(tool_code, base_permission) AS (VALUES
    ('todo.query', 'todo:read'),
    ('leave.mine', 'leave:read:self'),
    ('knowledge.search', 'knowledge:search'),
    ('notification.mine', 'notification:read:self'),
    ('leave.createDraft', 'leave:create'),
    ('leave.submit', 'leave:create'),
    ('leave.apply', 'leave:create')
)
INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT role_permission.tenant_id,
       role_permission.role_code,
       'agent:tool:' || tool_permission.tool_code
FROM rbac_role_permission role_permission
JOIN tool_permission ON tool_permission.base_permission = role_permission.permission_code
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'AI_PERMISSION',
    updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'ai-permission';
