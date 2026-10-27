ALTER TABLE attendance_setting
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'attendance:settings:manage','管理考勤设置','考勤管理',
       '允许更新当前租户的上下班时间与弹性考勤规则',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'attendance:settings:manage'
FROM rbac_role_permission source
WHERE source.permission_code='route:attendance-settings'
  AND source.role_code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:attendance.settings.update','Agent 更新考勤设置','AI 能力',
       '允许 Agent 经二次确认和版本校验后更新当前租户考勤规则',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:attendance.settings.update'
FROM rbac_role_permission WHERE permission_code='attendance:settings:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'attendance.settings.update','Update tenant attendance settings',
 'Updates one tenant''s work hours and flex rules using the current configuration version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["version","workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked"],"properties":{"version":{"type":"integer","minimum":0},"workStartTime":{"type":"string","pattern":"^(?:[01]\\d|2[0-3]):[0-5]\\d$"},"workEndTime":{"type":"string","pattern":"^(?:[01]\\d|2[0-3]):[0-5]\\d$"},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["version","workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked","updatedAt"],"properties":{"version":{"type":"integer","minimum":1},"workStartTime":{"type":"string"},"workEndTime":{"type":"string"},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:643217d55767222ce1f6aaee5fd923866924842409883215ab15305d52bb4cfd','L2',
 '["attendance:settings:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
