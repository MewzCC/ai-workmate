ALTER TABLE meeting_booking ADD COLUMN IF NOT EXISTS agent_cancel_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_meeting_booking_agent_cancel_operation
    ON meeting_booking(tenant_id, organizer_user_id, agent_cancel_operation_key)
    WHERE agent_cancel_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.cancel','Agent 取消本人会议预约','AI 能力',
       '允许 Agent 在显式确认后取消本人单条预约，仍需实时业务权限',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.cancel'
FROM rbac_role_permission WHERE permission_code='meeting:cancel'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.cancel','Cancel my meeting-room booking',
 'Cancels exactly one active meeting-room booking owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","roomId","status","version","cancelledAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"roomId":{"type":"integer","minimum":1},"status":{"type":"string","const":"CANCELLED"},"version":{"type":"integer","minimum":1},"cancelledAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:c3ba189f1f0dbee9207312b103eeacc6c4dea6d695aa196a5a61740f560e3c2c',
 'L1','["meeting:cancel"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
