INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dashboard.preferences.update','Agent 调整驾驶舱指标','AI 能力',
       '允许 Agent 经明确确认后调整当前用户自己的驾驶舱指标与顺序',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:dashboard.preferences.update'
FROM rbac_role_permission WHERE permission_code='dashboard:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'dashboard.preferences.update','Update my dashboard metrics',
 'Updates the authenticated user''s ordered dashboard metric selection.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["metricCodes"],"properties":{"metricCodes":{"type":"array","minItems":1,"maxItems":4,"uniqueItems":true,"items":{"type":"string","enum":["PENDING_TODOS","OVERDUE_TODOS","MY_APPLICATIONS","UNREAD_MESSAGES"]}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["metricCodes","availableMetricCodes"],"properties":{"metricCodes":{"type":"array","maxItems":4,"items":{"type":"string"}},"availableMetricCodes":{"type":"array","maxItems":4,"items":{"type":"string"}}}}'::jsonb,
 'sha256:bdfa0ed9774119150ea82446ad14fdebdfd5f88e21652a3bf82511301c8e9a96','L1','["dashboard:read"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
