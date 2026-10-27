INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:knowledge.base.update','Agent 更新本人知识库','AI 能力',
       '允许 Agent 经明确确认后更新当前用户一个知识库的元数据与检索参数',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:knowledge.base.update'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='knowledge:search'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'knowledge.base.update','Update one knowledge base',
 'Updates bounded metadata and retrieval settings on one knowledge base owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"minProperties":2,"required":["kbId"],"properties":{"kbId":{"type":"integer","minimum":1},"name":{"type":"string","minLength":1,"maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":"string","maxLength":500},"chunkSize":{"type":"integer","minimum":100,"maximum":8000},"chunkOverlap":{"type":"integer","minimum":0,"maximum":4000},"denseTopK":{"type":"integer","minimum":1,"maximum":50},"sparseTopK":{"type":"integer","minimum":0,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["knowledgeBaseId","name","icon","documentCount","chunkCount","chunkSize","chunkOverlap","denseTopK","sparseTopK","updatedAt"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":["string","null"],"maxLength":500},"documentCount":{"type":"integer","minimum":0},"chunkCount":{"type":"integer","minimum":0},"chunkSize":{"type":"integer","minimum":100,"maximum":8000},"chunkOverlap":{"type":"integer","minimum":0,"maximum":4000},"denseTopK":{"type":"integer","minimum":1,"maximum":50},"sparseTopK":{"type":"integer","minimum":0,"maximum":50},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:79f873ae75d3b83b062efa802cfd1aa22708fd41d7fdde6f373ad400457c66f2','L1',
 '["knowledge:search"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
