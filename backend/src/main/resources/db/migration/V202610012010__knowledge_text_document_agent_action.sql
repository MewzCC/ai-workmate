INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:knowledge.document.createText','Agent 创建知识库文本资料','AI 能力',
       '允许 Agent 经明确确认后在本人已有知识库中创建一篇受限长度文本资料',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:knowledge.document.createText'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='knowledge:search'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'knowledge.document.createText','Create one text knowledge document',
 'Creates one bounded text document in a knowledge base owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["kbId","filename","content"],"properties":{"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","minLength":1,"maxLength":255},"content":{"type":"string","minLength":1,"maxLength":12000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["documentId","kbId","filename","status","chunkCount","createdAt"],"properties":{"documentId":{"type":"integer","minimum":1},"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"status":{"type":"string","const":"READY"},"chunkCount":{"type":"integer","minimum":1},"createdAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:15e630e3f3e2b4555b462814140dbff020de8be9546f2a365d8c3e3e912a4e9a','L1',
 '["knowledge:search"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,30000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
