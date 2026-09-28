INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'route:platform-observability', '访问平台观测看板', '开放平台',
       '查看当前租户的运行趋势和脱敏聚合指标', tenant.id
FROM tenant WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type,
                       component_key, permission_code, sort_order, enabled, tenant_id)
SELECT 'platform-observability', 'integration', '平台观测',
       '/oa/platform-observability', NULL, 'PAGE',
       'PLATFORM_OBSERVABILITY', 'route:platform-observability', 3, TRUE, tenant.id
FROM tenant WHERE tenant.code = 'DEFAULT'
ON CONFLICT (route_key) DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'route:platform-observability', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN')
ON CONFLICT DO NOTHING;
