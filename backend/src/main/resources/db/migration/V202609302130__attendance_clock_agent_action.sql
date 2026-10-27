INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'attendance:clock','本人考勤打卡','考勤管理','允许用户按服务端当前时间为本人执行上班或下班打卡',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'attendance:clock'
FROM rbac_role_permission source
WHERE source.permission_code='route:attendance-clock'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:attendance.clock','Agent 本人考勤打卡','AI 能力',
       '允许 Agent 经显式确认后按服务端当前时间为本人执行一次上班或下班打卡',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:attendance.clock'
FROM rbac_role_permission source
WHERE source.permission_code='attendance:clock'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'attendance.clock','Clock my attendance',
 'Records one current-time clock-in or clock-out for the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["clockType"],"properties":{"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["recordId","clockDate","status","lateMinutes","earlyLeaveMinutes"],"properties":{"recordId":{"type":"integer","minimum":1},"clockDate":{"type":"string","format":"date"},"clockInTime":{"type":["string","null"],"format":"date-time"},"clockOutTime":{"type":["string","null"],"format":"date-time"},"status":{"type":"string","enum":["NORMAL","LATE","EARLY_LEAVE","LATE_AND_EARLY","MISSING_CLOCK"]},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:dd02d5489088fddfdb73f510972bf86b047b75470e72ac4c8293758f908bdd86','L1',
 '["attendance:clock"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
