INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:visitor.markArrived','Agent 确认访客到访','AI 能力',
       '允许 Agent 在显式确认后将本人申请或本人接待的一条已签到访客登记为已到访',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:visitor.markArrived'
FROM rbac_role_permission WHERE permission_code='visitor:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.markArrived','Mark one checked-in visitor as arrived',
 'Marks one checked-in visitor application related to the authenticated user as arrived.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version","occurredAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","const":"VISITED"},"version":{"type":"integer","minimum":1},"occurredAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:d42a274c0b07852fbef93d734436875a289a0565c45c2beb92d547d27375a4e3',
 'L1','["visitor:register"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
