INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:budget.activateDraft','Agent 启用预算草稿','AI 能力',
       '允许 Agent 在二次确认后按版本将一条租户内预算从草稿启用',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:budget.activateDraft'
FROM rbac_role_permission WHERE permission_code='budget:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'budget.activateDraft','Activate a budget draft',
 'Activates one tenant-scoped draft budget using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["budgetId","version"],"properties":{"budgetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"ACTIVE"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:51d4be688b529ca56eebc2cc04adcb6b67b0e929d15f68ea7f3a935d9c0e7611',
 'L2','["budget:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
