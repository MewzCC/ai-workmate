INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:supplier.updateStatus','Agent 变更供应商状态','AI 能力',
       '允许 Agent 经二次确认后变更一条供应商状态',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:supplier.updateStatus'
FROM rbac_role_permission source
WHERE source.permission_code='supplier:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'supplier.updateStatus','Update one supplier status',
 'Applies one allowed status transition to one tenant-scoped supplier using optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["supplierId","version","status"],"properties":{"supplierId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["ACTIVE","SUSPENDED","BLACKLISTED"]},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["supplierId","code","status","version","updatedAt"],"properties":{"supplierId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","enum":["ACTIVE","SUSPENDED","BLACKLISTED"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:0cb980da791f760e584b3d4a29d342f6668756b96451ea2c03f3539fd7401e4e','L2',
 '["supplier:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
