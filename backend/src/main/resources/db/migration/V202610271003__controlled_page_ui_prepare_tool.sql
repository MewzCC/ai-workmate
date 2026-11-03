-- This read-only tool prepares local UI state. Business writes still require their own Gateway task.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,'AI 能力',permission.description,tenant.id FROM tenant
CROSS JOIN (VALUES
 ('agent:ui:prepare','AI 页面交互准备','只准备导航、筛选和未提交的表单状态，不能提交业务'),
 ('agent:tool:page.ui.prepare','Agent 准备页面交互','固定 UI 指令，仍须浏览器按实时权限与已注册处理器校验')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT roles.tenant_id,roles.role_code,permission.code FROM rbac_role_permission roles
CROSS JOIN (VALUES ('agent:ui:prepare'),('agent:tool:page.ui.prepare')) AS permission(code)
WHERE roles.permission_code LIKE 'route:%' ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'page.ui.prepare','Prepare a page interaction',
 'Prepares one authorized browser navigation, filter, detail, unsaved form or refresh command.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["pageId","code"],"properties":{"pageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"code":{"type":"string","enum":["ui.navigate","ui.applyFilter","ui.openDetail","ui.openCreateForm","ui.fillForm","ui.previewSubmission","ui.refreshPage"]},"targetPageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"recordId":{"type":"integer","minimum":1},"fields":{"type":"array","maxItems":32,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,39}$"},"value":{"oneOf":[{"type":"string","maxLength":500},{"type":"number","minimum":-1000000000000,"maximum":1000000000000},{"type":"boolean"}]}}}}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["pageId","code","fields"],"properties":{"pageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"code":{"type":"string","enum":["ui.navigate","ui.applyFilter","ui.openDetail","ui.openCreateForm","ui.fillForm","ui.previewSubmission","ui.refreshPage"]},"targetPageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"recordId":{"type":"integer","minimum":1},"fields":{"type":"array","maxItems":32,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,39}$"},"value":{"oneOf":[{"type":"string","maxLength":500},{"type":"number","minimum":-1000000000000,"maximum":1000000000000},{"type":"boolean"}]}}}},"formContext":{"type":"object","additionalProperties":false,"required":["sessionId"],"properties":{"sessionId":{"type":"string","pattern":"^[a-zA-Z0-9_-]{16,64}$"},"recordId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}},"oneOf":[{"required":["recordId","version"]},{"not":{"anyOf":[{"required":["recordId"]},{"required":["version"]}]}}]}}}'::jsonb,'sha256:77f6759ba3f87f05aeeba0411ca3d5a7d532415543cdd8c1536a8ae3150292de',
 'L0','["agent:ui:prepare"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',
 32,32768,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

