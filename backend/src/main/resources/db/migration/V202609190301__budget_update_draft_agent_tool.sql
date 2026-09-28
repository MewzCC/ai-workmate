INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:budget.updateDraft','Agent 更新预算草稿','AI 能力',
       '允许 Agent 在显式确认后按版本更新一条租户内预算草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:budget.updateDraft'
FROM rbac_role_permission WHERE permission_code='budget:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'budget.updateDraft','Update a budget draft',
 'Updates one tenant-scoped draft budget using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["budgetId","version","name","fiscalYear","ownerUserId","totalAmount","currency","warningThreshold"],"properties":{"budgetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":160},"fiscalYear":{"type":"integer","minimum":2000,"maximum":2200},"ownerUserId":{"type":"integer","minimum":1},"totalAmount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"warningThreshold":{"type":"integer","minimum":1,"maximum":100},"summary":{"type":"string","maxLength":2000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:9fc377f5ffee46d9b770a4d7a588519e7b85884b2b077e18aa85b5aae4d79e5f',
 'L1','["budget:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
