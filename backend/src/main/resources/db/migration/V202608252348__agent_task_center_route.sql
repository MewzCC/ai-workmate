INSERT INTO rbac_permission (code, name, module, description)
VALUES ('route:ai-tasks', '访问个人 AI 任务中心', '页面访问', '查看并管理当前用户自己的 AI 任务')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_route (
    route_key, parent_key, name, path, icon, route_type, component_key,
    permission_code, sort_order, enabled
)
VALUES (
    'ai-tasks', 'workspace', 'AI 任务中心', '/oa/ai-tasks', 'ai-tasks', 'PAGE', 'AI_TASK_CENTER',
    'route:ai-tasks', 3, TRUE
)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key,
    name = EXCLUDED.name,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    route_type = EXCLUDED.route_type,
    component_key = EXCLUDED.component_key,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

UPDATE rbac_route SET sort_order = 4, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'todo' AND parent_key = 'workspace';

UPDATE rbac_route SET sort_order = 5, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'messages' AND parent_key = 'workspace';

INSERT INTO rbac_role_permission (role_code, permission_code)
SELECT code, 'route:ai-tasks'
FROM rbac_role
WHERE code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
ON CONFLICT DO NOTHING;
