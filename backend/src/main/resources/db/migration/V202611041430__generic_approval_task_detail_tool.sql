-- New bounded L0 capability only; existing tenant and write switches are unchanged.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.genericTask.detail.query','Agent 读取本人通用审批待办详情','AI 能力',
       '按冻结快照分页读取本人受理任务，隐藏敏感字段和附件，不执行审批',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.genericTask.detail.query'
FROM rbac_role_permission WHERE permission_code='todo:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.genericTask.detail.query','Read an assigned generic approval task',
 'Reads frozen fields of one assigned generic approval task, with sensitive fields redacted.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"integer","minimum":1},"fieldPage":{"type":"integer","minimum":1,"maximum":25}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["taskId","applicationId","applicantName","formName","title","status","version","instanceStatus","applicationStatus","overdue","fields","fieldsTotal","fieldPage","fieldPageSize","canApprove","canReject"],"properties":{"taskId":{"type":"integer","minimum":1},"applicationId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"title":{"type":"string","maxLength":200},"nodeName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"version":{"type":"integer","minimum":0},"instanceStatus":{"type":"string","enum":["RUNNING","COMPLETED","CANCELLED"]},"applicationStatus":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"fields":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","values","redacted"],"properties":{"name":{"type":"string","minLength":1,"maxLength":120},"label":{"type":"string","minLength":1,"maxLength":200},"type":{"type":"string","maxLength":40},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}},"redacted":{"type":"boolean"}}}},"fieldsTotal":{"type":"integer","minimum":0,"maximum":100},"fieldPage":{"type":"integer","minimum":1,"maximum":25},"fieldPageSize":{"type":"integer","enum":[4]},"canApprove":{"type":"boolean"},"canReject":{"type":"boolean"},"blockedReason":{"type":"string","maxLength":500}}}'::jsonb,
 'sha256:0c27f2dadc33dc945a007817198c781664f86e92bc1943e5a232d39bed90a26b','L0','["todo:read"]'::jsonb,
 'ALL','ASSIGNED_TO_SELF','READ_ONLY_SAFE','NONE','NONE',20,262144,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
