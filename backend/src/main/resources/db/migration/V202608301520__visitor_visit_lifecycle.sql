ALTER TABLE visitor_booking
    DROP CONSTRAINT IF EXISTS ck_visitor_status;

ALTER TABLE visitor_booking
    ADD COLUMN IF NOT EXISTS registered_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS visited_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS left_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS no_show_at TIMESTAMP;

ALTER TABLE visitor_booking
    ADD CONSTRAINT ck_visitor_status CHECK (
        status IN (
            'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN',
            'CHECKED_IN', 'VISITED', 'LEFT', 'NO_SHOW'
        )
    );

CREATE INDEX IF NOT EXISTS idx_visitor_visit_status
    ON visitor_booking (tenant_id, status, expected_visit_at)
    WHERE status IN ('APPROVED', 'CHECKED_IN', 'VISITED');

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT v.code, v.name, '行政资产', v.description, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('visitor:register', '登记本人访客到离场', '登记本人申请或本人接待访客的签到、到访、离场和失约'),
    ('visitor:register:any', '登记全部访客到离场', '登记租户内全部访客的签到、到访、离场和失约')
) AS v(code, name, description)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'visitor:register', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'visitor:register:any', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN')
ON CONFLICT DO NOTHING;
