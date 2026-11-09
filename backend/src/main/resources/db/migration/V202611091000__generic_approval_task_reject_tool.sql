-- Add one disabled L2 tool. Existing tools, tenant switches and page policies stay unchanged.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.genericTask.reject','Agent 驳回单个通用审批任务','AI 能力',
       '明确理由并二次确认后驳回本人受理的一个任务，校验任务版本和冻结流程',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.genericTask.reject'
FROM rbac_role_permission WHERE permission_code='approval:act'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.genericTask.reject','Reject one assigned generic task',
 'Rejects exactly one currently assigned generic approval node at its expected task version with a required reason.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["REJECTED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","enum":["REJECTED"]},"action":{"type":"string","enum":["REJECT"]}}}'::jsonb,
 'sha256:902b48b79d40b1538db4e8937e4e1d14bb6f428caa2e7ec60b3d84c61272b96d','L2','["approval:act"]'::jsonb,
 'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE','SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
