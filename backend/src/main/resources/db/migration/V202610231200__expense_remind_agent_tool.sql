INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.remind','Agent 催办本人费用申请','AI 能力',
       '显式确认后向当前有效审批人发送单条限频站内提醒；工具默认关闭',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:expense.remind'
FROM rbac_role_permission WHERE permission_code='approval:remind'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.remind','Remind my expense approver',
 'Sends one rate-limited internal reminder to the current approver of one owned pending expense application.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:4a38437ed858eee879c9cadcf868a4f21e3ad0b70b17efa4dff6f1c73d5578d3',
 'L1','["approval:remind"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
