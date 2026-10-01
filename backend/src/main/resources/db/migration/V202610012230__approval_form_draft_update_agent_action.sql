INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.form.updateDraft','Agent 更新审批表单草稿','AI 能力',
       '允许 Agent 经明确确认和乐观锁校验后更新一个未发布的审批表单定义草稿',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:approval.form.updateDraft'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'approval.form.updateDraft','Update an approval form draft',
 'Updates one disabled tenant approval form definition from bounded semantic fields and an expected version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["formId","version","formName","fields"],"properties":{"formId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1},"formName":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"fields":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]}}}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["formId","formKey","status","version","updatedAt"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:e7466382448ff0ccc7ed0fea44a49b95ac4016b70da63497a017d70e8712546a','L1',
 '["approval:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',1,16384,15000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
