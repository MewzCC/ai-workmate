INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:contract.updateDraft','Agent 更新合同草稿','AI 能力',
       '允许 Agent 在显式确认后按乐观锁更新一条租户内合同草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:contract.updateDraft'
FROM rbac_role_permission WHERE permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.updateDraft','Update a contract draft',
 'Updates one tenant-scoped draft contract using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["contractId","version","name","contractType","counterpartyName","ownerUserId","amount","currency","startDate","endDate"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":160},"contractType":{"type":"string","enum":["PURCHASE","SALES","SERVICE","LEASE","OTHER"]},"counterpartyName":{"type":"string","minLength":1,"maxLength":160},"supplierId":{"type":"integer","minimum":1},"ownerUserId":{"type":"integer","minimum":1},"amount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"signedDate":{"type":"string","format":"date"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"summary":{"type":"string","maxLength":2000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","status","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:c5b10829bb43ebcfd88fd91a1cd9b167d6c62f9fc1e359fb5788b03f33b10b95',
 'L1','["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
