INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.cancelDraft','Agent 取消本人费用草稿','AI 能力',
       '显式确认后取消单份本人费用草稿，不删除记录；工具默认关闭',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:expense.cancelDraft'
FROM rbac_role_permission WHERE permission_code='approval:cancel'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.cancelDraft','Cancel my expense draft',
 'Cancels one self-owned expense draft without deleting it or affecting an active approval.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"CANCELLED"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:a8b1701915dd62722f5ec22a97d3195b2671a66147dc448964ed83a15601490b',
 'L1','["approval:cancel"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
