ALTER TABLE attendance_reissue
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_attendance_reissue_agent_operation
    ON attendance_reissue(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:attendance.reissue.apply','Agent 提交本人补卡申请','AI 能力',
       '允许 Agent 在显式确认后为当前用户原子提交一条补卡申请，不直接修改考勤记录',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:attendance.reissue.apply'
FROM rbac_role_permission WHERE permission_code='attendance:reissue:apply'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'attendance.reissue.apply','Apply for my attendance correction',
 'Creates exactly one attendance correction request for the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["clockDate","clockType","reason"],"properties":{"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["reissueId","status","clockDate","clockType","submittedAt"],"properties":{"reissueId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"submittedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:bb3f7af3920e01290f14107d053425e8d986f988069eaa20031455ce49cce760',
 'L1','["attendance:reissue:apply"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
