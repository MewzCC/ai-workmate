-- V2：考勤管理模块（打卡、异常考勤、补卡申请、考勤统计）
-- 由 init.sql 中合并进入的考勤模块改动迁移而来（表、权限、路由、授权）。
-- 保持幂等，兼容 V1 已存在或尚未存在的两种情况。

-- 1. 考勤子页面访问权限
INSERT INTO rbac_permission(code, name, module, description, tenant_id) VALUES
    ('route:attendance-clock', '访问考勤打卡', '页面访问', '允许访问考勤打卡页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('route:attendance-exception', '访问异常考勤', '页面访问', '允许访问异常考勤页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('route:attendance-reissue', '访问补卡申请', '页面访问', '允许访问补卡申请页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('route:attendance-statistics', '访问考勤统计', '页面访问', '允许访问考勤统计页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

-- 2. 考勤由单页面调整为分组目录
INSERT INTO rbac_route(
    route_key, parent_key, name, path, icon, route_type, component_key,
    permission_code, sort_order, enabled, tenant_id
) VALUES
    ('attendance', 'hr', '考勤管理', NULL, NULL,
        'GROUP', NULL, NULL, 3, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (route_key) DO UPDATE SET
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

-- 3. 考勤子页面路由（含图标）
INSERT INTO rbac_route(
    route_key, parent_key, name, path, icon, route_type, component_key,
    permission_code, sort_order, enabled, tenant_id
) VALUES
    ('attendance-clock', 'attendance', '打卡', '/oa/attendance-clock', 'attendance-clock',
        'PAGE', 'ATTENDANCE_CLOCK', 'route:attendance-clock', 1, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('attendance-exception', 'attendance', '异常考勤', '/oa/attendance-exception', 'attendance-exception',
        'PAGE', 'ATTENDANCE_EXCEPTION', 'route:attendance-exception', 2, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('attendance-reissue', 'attendance', '补卡申请', '/oa/attendance-reissue', 'attendance-reissue',
        'PAGE', 'ATTENDANCE_REISSUE', 'route:attendance-reissue', 3, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('attendance-statistics', 'attendance', '考勤统计', '/oa/attendance-statistics', 'attendance-statistics',
        'PAGE', 'ATTENDANCE_STATISTICS', 'route:attendance-statistics', 4, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (route_key) DO UPDATE SET
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

-- 4. 考勤路由授权
INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role_code, permission_code, t.id
FROM tenant t
CROSS JOIN (
    VALUES
        ('EMPLOYEE', 'route:attendance-clock'),
        ('EMPLOYEE', 'route:attendance-exception'),
        ('EMPLOYEE', 'route:attendance-reissue'),
        ('EMPLOYEE', 'route:attendance-statistics'),
        ('SYSTEM_ADMIN', 'route:attendance-clock'),
        ('SYSTEM_ADMIN', 'route:attendance-exception'),
        ('SYSTEM_ADMIN', 'route:attendance-reissue'),
        ('SYSTEM_ADMIN', 'route:attendance-statistics'),
        ('PROCESS_ADMIN', 'route:attendance-clock'),
        ('PROCESS_ADMIN', 'route:attendance-exception'),
        ('PROCESS_ADMIN', 'route:attendance-reissue'),
        ('PROCESS_ADMIN', 'route:attendance-statistics'),
        ('FINANCE_ADMIN', 'route:attendance-clock'),
        ('FINANCE_ADMIN', 'route:attendance-exception'),
        ('FINANCE_ADMIN', 'route:attendance-reissue'),
        ('FINANCE_ADMIN', 'route:attendance-statistics')
) AS defaults(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT 'SUPER_ADMIN', code, tenant_id
FROM rbac_permission
WHERE code IN (
    'route:attendance-clock',
    'route:attendance-exception',
    'route:attendance-reissue',
    'route:attendance-statistics'
)
ON CONFLICT DO NOTHING;

-- 5. 打卡记录与补卡申请表
CREATE TABLE IF NOT EXISTS attendance_record (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    clock_date          DATE NOT NULL,
    clock_in_time       TIMESTAMP,
    clock_out_time      TIMESTAMP,
    clock_in_ip         VARCHAR(64),
    clock_out_ip        VARCHAR(64),
    status              VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    late_minutes        INTEGER NOT NULL DEFAULT 0,
    early_leave_minutes INTEGER NOT NULL DEFAULT 0,
    source              VARCHAR(20) NOT NULL DEFAULT 'WEB',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_attendance_status CHECK (
        status IN ('NORMAL', 'LATE', 'EARLY_LEAVE', 'LATE_AND_EARLY', 'MISSING_CLOCK')
    ),
    CONSTRAINT ck_attendance_source CHECK (source IN ('WEB', 'H5', 'API')),
    CONSTRAINT uk_attendance_user_date UNIQUE (tenant_id, user_id, clock_date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_user_date
    ON attendance_record(tenant_id, user_id, clock_date DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_date_status
    ON attendance_record(tenant_id, clock_date, status);

CREATE TABLE IF NOT EXISTS attendance_reissue (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    applicant_user_id   BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    approver_user_id    BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    clock_date          DATE NOT NULL,
    clock_type          VARCHAR(10) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_comment    VARCHAR(500),
    submitted_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reissue_clock_type CHECK (clock_type IN ('CLOCK_IN', 'CLOCK_OUT')),
    CONSTRAINT ck_reissue_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_reissue_applicant_status
    ON attendance_reissue(tenant_id, applicant_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reissue_approver_status
    ON attendance_reissue(tenant_id, approver_user_id, status, submitted_at DESC);
