INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dictionary.type.create','Agent 创建字典类型','AI 能力',
       '允许 Agent 经二次确认后创建一个租户内启用的字典类型',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:dictionary.type.create'
FROM rbac_role_permission grant_row WHERE grant_row.permission_code='dictionary:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'dictionary.type.create','Create one dictionary type',
 'Creates one active tenant dictionary type with bounded non-sensitive metadata.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["dictionaryTypeId","code","name","status","sortOrder","version","updatedAt"],"properties":{"dictionaryTypeId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":120},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","const":"ACTIVE"},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"version":{"type":"integer","minimum":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:89f0b84f72ca1c6a026e551921ddedb7b38aa69ab2abe91cd16e66c8f7797cdc','L2',
 '["dictionary:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
