INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:leave.withdraw','Agent 撤回本人请假申请','AI 能力',
       '允许 Agent 显式确认后撤回本人单条待审批请假申请，禁止自动重试',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:leave.withdraw'
FROM rbac_role_permission WHERE permission_code='leave:withdraw'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'leave.withdraw','Withdraw my leave application',
 'Withdraws exactly one pending leave application owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:8d093edc7124a4cd99ba92352223c2934524fe4a022d63916dc33513844571a9',
 'L1','["leave:withdraw"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
