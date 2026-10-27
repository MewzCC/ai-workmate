INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dictionary.type.update','Agent 修改字典类型','AI 能力',
       '允许 Agent 经明确确认后按不可变编码和乐观锁版本修改一个字典类型的非敏感元数据',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:dictionary.type.update'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='dictionary:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'dictionary.type.update','Update one dictionary type',
 'Updates bounded metadata on one tenant dictionary type selected by its immutable code.','1.0.0',
 '{"type":"object","additionalProperties":false,"minProperties":3,"required":["code","version"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["dictionaryTypeId","code","name","status","sortOrder","version","updatedAt"],"properties":{"dictionaryTypeId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":120},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:1885b4e3284a75c88784f5318f5fbf0a7fb0c12a31699eb4cb4e7e73101d5678','L1',
 '["dictionary:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
