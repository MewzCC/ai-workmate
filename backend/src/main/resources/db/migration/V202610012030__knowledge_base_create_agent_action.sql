INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:knowledge.base.create','Agent 创建本人知识库','AI 能力',
       '允许 Agent 经明确确认后为当前用户创建一个知识库',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:knowledge.base.create'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='knowledge:search'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'knowledge.base.create','Create one knowledge base',
 'Creates one knowledge base owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["knowledgeBaseId","name","icon","documentCount","chunkCount","createdAt"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":["string","null"],"maxLength":500},"documentCount":{"type":"integer","minimum":0},"chunkCount":{"type":"integer","minimum":0},"createdAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:e397aeffc2f83042328b36a7308a762abac81935ffd00c2face07d301aeeb8c1','L1',
 '["knowledge:search"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
