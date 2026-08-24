-- V3：考勤上下班时间配置（支持弹性上下班）＋ 考勤设置页
-- 新增 attendance_setting：每个租户一行，配置标准上班/下班时间与弹性宽限分钟。
-- 迟到/早退判定与补卡“视为准时”时间一律以该配置为准；
-- 缺省 09:00 / 18:00、宽限 0 分钟，与旧写死常量行为完全一致。
-- 同时注册「考勤设置」页面（仅系统管理员可见）。
-- 保持幂等：新库（V2 之后）与旧库（V2 已建）均可安全执行多次。

-- 1. 考勤设置页访问权限
INSERT INTO rbac_permission(code, name, module, description, tenant_id) VALUES (
    'route:attendance-settings', '访问考勤设置', '页面访问', '允许配置上下班时间与弹性宽限',
    (SELECT id FROM tenant WHERE code = 'DEFAULT')
) ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

-- 2. 考勤设置页路由（挂在考勤目录下，图标复用「设置」）
INSERT INTO rbac_route(
    route_key, parent_key, name, path, icon, route_type, component_key,
    permission_code, sort_order, enabled, tenant_id
) VALUES (
    'attendance-settings', 'attendance', '考勤设置', '/oa/attendance-settings', 'settings',
    'PAGE', 'ATTENDANCE_SETTINGS', 'route:attendance-settings', 5, TRUE,
    (SELECT id FROM tenant WHERE code = 'DEFAULT')
) ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key,
    name = EXCLUDED.name,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    route_type = EXCLUDED.route_type,
    component_key = EXCLUDED.component_key,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    enabled = TRUE,
    tenant_id = EXCLUDED.tenant_id,
    updated_at = CURRENT_TIMESTAMP;

-- 3. 考勤设置页授权（仅系统级管理员）
INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role_code, permission_code, t.id
FROM tenant t
CROSS JOIN (
    VALUES
        ('SUPER_ADMIN', 'route:attendance-settings'),
        ('SYSTEM_ADMIN', 'route:attendance-settings')
) AS defaults(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

-- 4. 上下班时间配置表
CREATE TABLE IF NOT EXISTS attendance_setting (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    work_start_time    TIME NOT NULL DEFAULT '09:00:00',
    work_end_time      TIME NOT NULL DEFAULT '18:00:00',
    start_flex_minutes INTEGER NOT NULL DEFAULT 0,
    end_flex_minutes   INTEGER NOT NULL DEFAULT 0,
    updated_by         BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attendance_setting_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_attendance_setting_start_flex CHECK (start_flex_minutes >= 0 AND start_flex_minutes <= 480),
    CONSTRAINT ck_attendance_setting_end_flex   CHECK (end_flex_minutes >= 0 AND end_flex_minutes <= 480),
    CONSTRAINT ck_attendance_setting_work_hours CHECK (work_start_time <> work_end_time)
);

-- 5. 为所有租户补齐默认配置（仅缺失时插入，避免覆盖已有配置）
INSERT INTO attendance_setting(tenant_id, work_start_time, work_end_time, start_flex_minutes, end_flex_minutes)
SELECT id, '09:00:00', '18:00:00', 0, 0
FROM tenant
ON CONFLICT (tenant_id) DO NOTHING;
