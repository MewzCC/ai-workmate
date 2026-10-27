INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dictionary.item.query','Agent 查询字典项','AI 能力',
       '允许 Agent 按不可变类型编码分页查询当前租户字典项及版本，不返回数据库内部 ID',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:dictionary.item.query'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='dictionary:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'dictionary.item.query','Query dictionary items',
 'Returns a bounded page of tenant dictionary items selected by immutable type code without internal identifiers.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["typeCode"],"properties":{"typeCode":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"keyword":{"type":"string","maxLength":100},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["typeCode","records","total","page","size","canManage"],"properties":{"typeCode":{"type":"string","maxLength":64},"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["value","label","status","sortOrder","usageCount","version","updatedAt"],"properties":{"value":{"type":"string","maxLength":128},"label":{"type":"string","maxLength":160},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","maxLength":40},"sortOrder":{"type":"integer","minimum":0},"usageCount":{"type":"integer","minimum":0},"version":{"type":"integer","minimum":0},"updatedAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50},"canManage":{"type":"boolean"}}}'::jsonb,
 'sha256:a292013677a883e78f42ffe1c2c99c99896f45f354d7d8d9780a0168543520c5','L0',
 '["dictionary:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,
 'READ_METADATA',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
