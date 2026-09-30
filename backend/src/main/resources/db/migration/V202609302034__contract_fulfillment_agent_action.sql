INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:contract.updateFulfillment','Agent 变更合同履约状态','AI 能力',
       '允许 Agent 经二次确认后变更一份生效合同的履约进度',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:contract.updateFulfillment'
FROM rbac_role_permission source
WHERE source.permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.updateFulfillment','Update one contract fulfillment status',
 'Applies one allowed fulfillment transition to one active tenant-scoped contract using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["contractId","version","status"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["IN_PROGRESS","FULFILLED","BREACHED"]},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["contractId","code","status","fulfillmentStatus","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"ACTIVE"},"fulfillmentStatus":{"type":"string","enum":["IN_PROGRESS","FULFILLED","BREACHED"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:d6db3babd6a6432c6f4621cf711fffeda272c3b65f18d34266700235dd620556','L2',
 '["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
