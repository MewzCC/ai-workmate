INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:contract.updateStatus','Agent 变更合同状态','AI 能力',
       '允许 Agent 经二次确认后变更一份合同的生命周期状态',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:contract.updateStatus'
FROM rbac_role_permission source
WHERE source.permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.updateStatus','Update one contract status',
 'Applies one allowed lifecycle transition to one tenant-scoped contract using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["contractId","version","status"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["ACTIVE","COMPLETED","TERMINATED"]},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","status","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","enum":["ACTIVE","COMPLETED","TERMINATED"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:10210557fa790ede65d98aadab50a9ea4a82648c21299e731e69e1f61008ab1a','L2',
 '["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
