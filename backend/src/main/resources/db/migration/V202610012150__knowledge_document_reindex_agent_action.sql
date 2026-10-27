INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:knowledge.document.reindex','Agent 重建本人知识资料索引','AI 能力',
       '允许 Agent 经明确确认后重建当前用户一篇知识资料的向量索引',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:knowledge.document.reindex'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='knowledge:search'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'knowledge.document.reindex','Reindex one knowledge document',
 'Rebuilds embeddings for one knowledge document owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["documentId"],"properties":{"documentId":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["documentId","filename","status","chunkCount","updatedAt"],"properties":{"documentId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"status":{"type":"string","const":"READY"},"chunkCount":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:9b0d45dc6506d6b7e5f342d62f71aba31cc2a7e3d993fb7eab30c38528356205','L1',
 '["knowledge:search"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,30000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
