INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:contract.recordPayment','Agent 登记合同付款','AI 能力',
       '允许 Agent 经二次确认后为一份生效合同登记单笔付款',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:contract.recordPayment'
FROM rbac_role_permission source
WHERE source.permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.recordPayment','Record one contract payment',
 'Records one bounded payment against one active tenant-scoped contract using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["contractId","version","amount","paymentDate","reference"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"amount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"paymentDate":{"type":"string","format":"date"},"reference":{"type":"string","minLength":1,"maxLength":100},"note":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","status","amount","paidAmount","currency","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"ACTIVE"},"amount":{"type":"number","minimum":0.01},"paidAmount":{"type":"number","minimum":0},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:a9d8ba8b0aaedf6bc8e18d201f28f349f73c45111548a179fb8ca5021341c585','L2',
 '["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
