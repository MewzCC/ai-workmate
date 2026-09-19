INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:budget.createDraft','Agent 创建预算草稿','AI 能力',
       '允许 Agent 在显式确认后创建一条租户内预算草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:budget.createDraft'
FROM rbac_role_permission WHERE permission_code='budget:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'budget.createDraft','Create a budget draft',
 'Creates one tenant-scoped budget plan in draft status without activating or consuming it.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name","fiscalYear","ownerUserId","totalAmount","currency","warningThreshold"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"fiscalYear":{"type":"integer","minimum":2000,"maximum":2200},"ownerUserId":{"type":"integer","minimum":1},"totalAmount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"warningThreshold":{"type":"integer","minimum":1,"maximum":100},"summary":{"type":"string","maxLength":2000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:30d568a5b7743a291b88653c2548d4a40c39561e8647f26f155abc26a0f1cbca',
 'L1','["budget:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
