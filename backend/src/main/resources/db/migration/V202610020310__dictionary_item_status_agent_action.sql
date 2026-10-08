INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dictionary.item.updateStatus','Agent 启停字典项','AI 能力',
       '允许 Agent 经二次确认后按类型编码、不可变字典值和版本启用或停用单个租户字典项',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:dictionary.item.updateStatus'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='dictionary:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'dictionary.item.updateStatus','Update one dictionary item status',
 'Activates or disables one tenant dictionary item selected by immutable type code, value and version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["typeCode","value","version","status"],"properties":{"typeCode":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"value":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$","maxLength":128},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["ACTIVE","DISABLED"]}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["dictionaryItemId","typeCode","value","label","status","sortOrder","usageCount","version","updatedAt"],"properties":{"dictionaryItemId":{"type":"integer","minimum":1},"typeCode":{"type":"string","maxLength":64},"value":{"type":"string","maxLength":128},"label":{"type":"string","maxLength":160},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"usageCount":{"type":"integer","minimum":0},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:a427c5f87244f9808d49c39e3f35905928e36cc0933bb6c34dfacef16dc12acd','L2',
 '["dictionary:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','SECONDARY',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
