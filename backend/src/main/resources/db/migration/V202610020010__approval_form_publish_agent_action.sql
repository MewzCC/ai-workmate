INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.form.publishDraft','Agent 发布审批表单草稿','AI 能力',
       '允许 Agent 经二次确认后按版本发布一个已校验的审批表单草稿',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:approval.form.publishDraft'
FROM rbac_role_permission grant_row WHERE grant_row.permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'approval.form.publishDraft','Publish an approval form draft',
 'Publishes one disabled tenant approval form using an expected version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["formId","version"],"properties":{"formId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["formId","formKey","status","version","updatedAt"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"ENABLED"},"version":{"type":"integer","minimum":2},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:f7edbba87f8613de2b780a4d15083b76b995be9031eb23da1432771f854085e9','L2',
 '["approval:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','SECONDARY',1,4096,15000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
