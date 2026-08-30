CREATE TABLE IF NOT EXISTS meeting_booking (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    room_id BIGINT NOT NULL REFERENCES meeting_room(id) ON DELETE RESTRICT,
    organizer_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    title VARCHAR(120) NOT NULL,
    agenda VARCHAR(500),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    attendee_count INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    version INTEGER NOT NULL DEFAULT 0,
    cancelled_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meeting_booking_time CHECK (end_at > start_at),
    CONSTRAINT ck_meeting_booking_attendee CHECK (attendee_count > 0),
    CONSTRAINT ck_meeting_booking_status CHECK (status IN ('BOOKED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_meeting_booking_room_time
    ON meeting_booking (tenant_id, room_id, start_at, end_at)
    WHERE status = 'BOOKED';

CREATE INDEX IF NOT EXISTS idx_meeting_booking_organizer
    ON meeting_booking (tenant_id, organizer_user_id, start_at DESC);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT v.code, v.name, '行政资产', v.description, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('meeting:book', '预约会议室', '创建会议室预约'),
    ('meeting:read:self', '查看我的会议室预约', '查看本人创建的会议室预约'),
    ('meeting:cancel', '取消我的会议室预约', '取消本人尚未结束的会议室预约')
) AS v(code, name, description)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, permission.code, role.tenant_id
FROM rbac_role role
JOIN rbac_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
  AND permission.code IN ('meeting:book', 'meeting:read:self', 'meeting:cancel')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'route:meeting-room', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
ON CONFLICT DO NOTHING;
