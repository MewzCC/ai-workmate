INSERT INTO rbac_route(route_key, parent_key, name, icon, route_type, sort_order, enabled, tenant_id)
SELECT 'ai-config', settings.route_key, 'AI 配置', 'ai', 'MENU', 3, TRUE, settings.tenant_id
FROM rbac_route AS settings
WHERE settings.route_key = 'settings' AND settings.route_type = 'GROUP'
ON CONFLICT (route_key) DO NOTHING;

-- Reorganize only the built-in pages still directly under System Settings.
UPDATE rbac_route AS page
SET parent_key = folder.route_key,
    sort_order = entry.sort_order,
    name = CASE WHEN page.route_key = 'system-config' AND page.name = '系统配置'
                THEN '模型与偏好' ELSE page.name END,
    updated_at = CURRENT_TIMESTAMP
FROM rbac_route AS folder
CROSS JOIN (VALUES ('system-config', 1), ('ai-permission', 2), ('knowledge-base', 3))
    AS entry(route_key, sort_order)
WHERE folder.route_key = 'ai-config'
  AND folder.parent_key = 'settings'
  AND folder.route_type = 'MENU'
  AND folder.enabled
  AND page.tenant_id = folder.tenant_id
  AND page.parent_key = folder.parent_key
  AND page.route_type = 'PAGE'
  AND page.route_key = entry.route_key;
