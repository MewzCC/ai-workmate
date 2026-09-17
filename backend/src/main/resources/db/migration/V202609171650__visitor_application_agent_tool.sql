ALTER TABLE visitor_booking
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_visitor_booking_agent_key
    ON visitor_booking(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:visitor.apply','Agent 提交访客申请','AI 能力',
       '允许 Agent 在显式确认后为本人提交一条访客预约审批申请',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:visitor.apply'
FROM rbac_role_permission WHERE permission_code='visitor:create'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.apply','Submit one visitor booking application',
 'Creates one approval-bound visitor booking owned by the authenticated applicant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["visitorName","purpose","hostUserId","expectedVisitAt","partySize"],"properties":{"visitorName":{"type":"string","minLength":1,"maxLength":60},"visitorCompany":{"type":"string","maxLength":120},"visitorPhone":{"type":"string","maxLength":40},"purpose":{"type":"string","minLength":1,"maxLength":200},"hostUserId":{"type":"integer","minimum":1},"expectedVisitAt":{"type":"string","format":"date-time"},"expectedLeaveAt":{"type":"string","format":"date-time"},"plateNumber":{"type":"string","maxLength":40},"partySize":{"type":"integer","minimum":1,"maximum":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version","submittedAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:c4bc3759bc77b4289d9391e14f018f6680234c4d1becbc0099bb207a434a877b',
 'L1','["visitor:create"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
