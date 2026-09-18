INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.submitDraft','Agent 提交费用草稿','AI 能力',
       '允许 Agent 在显式确认后提交当前用户的一条费用报销草稿并启动审批流程',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:expense.submitDraft'
FROM rbac_role_permission WHERE permission_code='approval:submit'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.submitDraft','Submit my expense draft',
 'Submits one self-owned expense draft and starts its approval workflow.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:4a38437ed858eee879c9cadcf868a4f21e3ad0b70b17efa4dff6f1c73d5578d3',
 'L1','["approval:submit"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
