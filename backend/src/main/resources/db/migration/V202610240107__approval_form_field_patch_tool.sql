INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.form.patchDraftField','Agent 局部修改表单草稿字段','AI 能力',
       '明确确认后仅修改一个已有字段的标签、必填、占位或宽度，保留其他字段及约束；默认关闭',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.form.patchDraftField'
FROM rbac_role_permission WHERE permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.form.patchDraftField','Patch one approval form draft field',
 'Changes only label, required, placeholder or width of one existing field in one disabled tenant form using an expected version.','1.0.0',
 '{"type":"object","additionalProperties":false,"minProperties":4,"required":["formId","version","fieldName"],"properties":{"formId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1,"maximum":2147483646},"fieldName":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"width":{"type":"string","enum":["full","half"]}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["formId","formKey","status","version","fieldName","label","required","placeholder","width"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":2,"maximum":2147483647},"fieldName":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"width":{"type":"string","enum":["full","half"]}}}'::jsonb,
 'sha256:2d7f9c12e2c7bc7a72f986c077ba8de9a5b81f69d23342f1e8408959f8369744',
 'L1','["approval:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
