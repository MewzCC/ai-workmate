INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.process.publishDraft','Agent 发布审批流程草稿','AI 能力',
       '允许 Agent 经二次确认后按版本发布一个已校验的审批流程草稿',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:approval.process.publishDraft'
FROM rbac_role_permission grant_row WHERE grant_row.permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'approval.process.publishDraft','Publish an approval process draft',
 'Publishes one disabled tenant approval process using an expected version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["processId","version"],"properties":{"processId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["processId","processKey","status","version","updatedAt"],"properties":{"processId":{"type":"integer","minimum":1},"processKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"ENABLED"},"version":{"type":"integer","minimum":2},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:3be228efd5386ea292967cb9bf6e027e051798b2ee67b2c8dbff6b0af5fd1113','L2',
 '["approval:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','SECONDARY',1,4096,15000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
