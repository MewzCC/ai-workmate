INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:contract.remind','Agent 提醒合同到期','AI 能力',
       '允许 Agent 经显式确认后发送一条受频率限制的合同到期站内提醒',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:contract.remind'
FROM rbac_role_permission source
WHERE source.permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.remind','Remind one contract owner',
 'Sends one rate-limited internal expiry reminder for one due tenant-scoped contract.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["contractId","version"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","expiryState","reminderCount","lastRemindedAt","version"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"expiryState":{"type":"string","enum":["EXPIRING","EXPIRED"]},"reminderCount":{"type":"integer","minimum":1},"lastRemindedAt":{"type":"string","format":"date-time"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:578ed3fa7179edbd9e6e5b874edd2ae196248d0333dbe631bf41bc9db5b90321','L1',
 '["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
