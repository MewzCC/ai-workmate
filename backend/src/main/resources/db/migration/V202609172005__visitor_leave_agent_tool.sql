INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:visitor.leave','Agent 登记访客离场','AI 能力',
       '允许 Agent 在显式确认后为本人申请或本人接待的一条已到访访客登记离场',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:visitor.leave'
FROM rbac_role_permission WHERE permission_code='visitor:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.leave','Register one visitor departure',
 'Registers departure for one visited application related to the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version","occurredAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","const":"LEFT"},"version":{"type":"integer","minimum":1},"occurredAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:1c372b6905396188d7f748da1ef6fae69168fc635a69c44e2f3e5e87003fe0c7',
 'L1','["visitor:register"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
