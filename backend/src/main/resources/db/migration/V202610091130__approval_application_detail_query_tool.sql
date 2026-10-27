INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.application.detail.query','Agent 读取本人通用申请详情','AI 能力',
       '允许按历史表单快照读取本人申请，隐藏敏感字段与附件',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.application.detail.query'
FROM rbac_role_permission WHERE permission_code='approval:mine:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.application.detail.query','Read my generic application detail',
 'Reads self-owned application fields through frozen schema, redacting sensitive and unsupported values.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId"],"properties":{"applicationId":{"type":"integer","minimum":1},"fieldPage":{"type":"integer","minimum":1,"maximum":2}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["application","schemaSource","fields","fieldsTotal","fieldPage"],"properties":{"application":{"type":"object","additionalProperties":false,"required":["applicationId","formKey","formName","status","version","overdue","reminderCount","canWithdraw","canEditDraft","canCancel","canRemind"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"reminderCount":{"type":"integer","minimum":0},"canWithdraw":{"type":"boolean"},"canEditDraft":{"type":"boolean"},"canCancel":{"type":"boolean"},"canRemind":{"type":"boolean"}}},"schemaSource":{"type":"string","enum":["SNAPSHOT","CURRENT_DRAFT"]},"formVersion":{"type":"integer","minimum":0},"fields":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","values","redacted"],"properties":{"name":{"type":"string","minLength":1,"maxLength":120},"label":{"type":"string","minLength":1,"maxLength":200},"type":{"type":"string","maxLength":40},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}},"redacted":{"type":"boolean"}}}},"fieldsTotal":{"type":"integer","minimum":0,"maximum":100},"fieldPage":{"type":"integer","minimum":1,"maximum":2}}}'::jsonb,
 'sha256:ac5b12b6c1316ba942c2b8816ad1991d92f51aecb1fddd46bff79803bb456777','L0','["approval:mine:read"]'::jsonb,
 'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,65536,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
