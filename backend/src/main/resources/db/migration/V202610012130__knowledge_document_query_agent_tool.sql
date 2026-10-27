INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:knowledge.document.query','Agent 查询本人知识库资料','AI 能力',
       '允许 Agent 读取当前用户指定知识库中的资料列表或单篇安全摘要',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:knowledge.document.query'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='knowledge:search'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'knowledge.document.query','Query my knowledge documents',
 'Returns bounded document metadata from one knowledge base owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["kbId"],"properties":{"kbId":{"type":"integer","minimum":1},"documentId":{"type":"integer","minimum":1},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["knowledgeBaseId","records","total","page","size"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"records":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["documentId","filename","fileSize","fileType","chunkCount","status","createdAt","updatedAt"],"properties":{"documentId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"fileSize":{"type":"integer","minimum":0},"fileType":{"type":"string","maxLength":20},"chunkCount":{"type":"integer","minimum":0},"status":{"type":"string","maxLength":32},"createdAt":{"type":"string","format":"date-time"},"updatedAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
 'sha256:b34bbef6fe8e164e15901ed574a0ae0647cea357bdec3d9ce4c12400b138aeea','L0',
 '["knowledge:search"]'::jsonb,'ALL','FIXED_RESOURCE','READ_ONLY_SAFE','NONE','NONE',20,32768,10000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
