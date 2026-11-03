-- Applicant-only read metadata; existing administrative form permissions stay unchanged.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.application.template.query','Agent 查询可申请模板','AI 能力','只读启用表单的受控字段与可用流程标识',id
FROM tenant WHERE code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.application.template.query'
FROM rbac_role_permission WHERE permission_code='approval:create' ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.application.template.query','Query available application templates','Lists enabled applicant templates, with bounded semantic fields when an exact formKey is supplied.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":[],"properties":{"keyword":{"type":"string","maxLength":120},"formKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["formId","formKey","formName","version","fields"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"formName":{"type":"string","maxLength":120},"version":{"type":"integer","minimum":0},"fields":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]},"min":{"type":"number","minimum":-1000000000000000,"maximum":1000000000000000}}}},"processKey":{"type":"string","maxLength":64},"processName":{"type":"string","maxLength":120},"processVersion":{"type":"integer","minimum":0}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,'sha256:2b42e7d61ca55522686379feee80e732096212aff579fa25c43dd806a1bef94a',
 'L0','["approval:create"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',
 20,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

