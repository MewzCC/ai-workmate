INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.form.definition.query','Agent 读取表单字段定义','AI 能力',
       '只读当前租户一份表单的受控字段和版本，不返回原始结构或申请值',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.form.definition.query'
FROM rbac_role_permission WHERE permission_code='approval:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.form.definition.query','Read an approval form definition',
 'Returns one authorized tenant form definition as bounded semantic fields and its current version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["formId"],"properties":{"formId":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["formId","formKey","formName","status","version","fields"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"formName":{"type":"string","maxLength":120},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0},"fields":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]}}}}}}'::jsonb,
 'sha256:ca2407af55e1fa1053bed2a3ebf77f781e9f9c0ad5496979785cdbbe938c997e',
 'L0','["approval:read"]'::jsonb,'ALL','FIXED_RESOURCE','READ_ONLY_SAFE','NONE','NONE',
 20,65536,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
