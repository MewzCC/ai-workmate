ALTER TABLE meeting_booking
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_meeting_booking_agent_operation
    ON meeting_booking(tenant_id, organizer_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.book','Agent 预约会议室','AI 能力',
       '允许 Agent 为当前用户原子创建一条会议室预约，仍需显式确认',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.book'
FROM rbac_role_permission WHERE permission_code='meeting:book'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.book','Book a meeting room',
 'Creates exactly one meeting-room booking owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["roomId","title","startAt","endAt","attendeeCount"],"properties":{"roomId":{"type":"integer","minimum":1},"title":{"type":"string","minLength":1,"maxLength":120},"agenda":{"type":"string","maxLength":500},"startAt":{"type":"string","format":"date-time"},"endAt":{"type":"string","format":"date-time"},"attendeeCount":{"type":"integer","minimum":1,"maximum":10000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","roomId","status","version","startAt","endAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"roomId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["BOOKED","CANCELLED"]},"version":{"type":"integer","minimum":0},"startAt":{"type":"string","format":"date-time"},"endAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:08abfb0571d3c4b1576423f7aae0849039062dccca5025efef23e9cb0e88f276',
 'L1','["meeting:book"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,16384,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
