INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:contract.createDraft','Agent 创建合同草稿','AI 能力',
       '允许 Agent 在显式确认后创建一条租户内合同草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:contract.createDraft'
FROM rbac_role_permission WHERE permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.createDraft','Create a contract draft',
 'Creates one tenant-scoped contract draft without activating, signing, paying or sending it.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name","contractType","counterpartyName","ownerUserId","amount","currency","startDate","endDate"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"contractType":{"type":"string","enum":["PURCHASE","SALES","SERVICE","LEASE","OTHER"]},"counterpartyName":{"type":"string","minLength":1,"maxLength":160},"supplierId":{"type":"integer","minimum":1},"ownerUserId":{"type":"integer","minimum":1},"amount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"signedDate":{"type":"string","format":"date"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"summary":{"type":"string","maxLength":2000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","status","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:bd88de26d791f44a7d830c3786729fd65efa377500a30a3e0539758d646cba68',
 'L1','["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
