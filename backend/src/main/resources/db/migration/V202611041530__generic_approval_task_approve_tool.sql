-- Register one L2 write; release switches and the new tool remain disabled.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.genericTask.approve','Agent 通过单个通用审批节点','AI 能力',
       '二次确认后通过本人受理的一个通用审批节点，按任务版本和冻结流程校验',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.genericTask.approve'
FROM rbac_role_permission WHERE permission_code='approval:act'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.genericTask.approve','Approve one assigned generic task',
 'Approves exactly one currently assigned generic approval node at its expected task version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","enum":["PENDING","APPROVED"]},"action":{"type":"string","enum":["APPROVE"]}}}'::jsonb,
 'sha256:c032df3ef5293e8fcf69271882635e3580eeec2097d911ce68d1cebb0808076d','L2','["approval:act"]'::jsonb,
 'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE','SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
