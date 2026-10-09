INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'approval:mine:read','查询本人通用申请','流程审批','查询本人通用申请摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'approval:mine:read'
FROM rbac_role_permission WHERE permission_code='route:approval-start'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.application.mine.query','Agent 查询本人通用申请','AI 能力',
       '允许 Agent 分页读取本人通用申请摘要，不返回表单原始值或他人身份',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.application.mine.query'
FROM rbac_role_permission WHERE permission_code='route:approval-start'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.application.mine.query','Query my generic applications',
 'Reads self-owned generic application summaries without raw form data or identities.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"formKey":{"type":"string","minLength":1,"maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["applicationId","formKey","formName","status","version","overdue","reminderCount","canWithdraw","canEditDraft","canCancel","canRemind"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"reminderCount":{"type":"integer","minimum":0},"canWithdraw":{"type":"boolean"},"canEditDraft":{"type":"boolean"},"canCancel":{"type":"boolean"},"canRemind":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:f0f343b67ee165583e7d62914ac9037fa659fe2dd470b1fb7c7c27fb2328af74','L0','["approval:mine:read"]'::jsonb,
 'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,32768,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

