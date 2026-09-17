ALTER TABLE seal_usage
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_seal_usage_agent_key
    ON seal_usage(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:seal.apply','Agent 提交用印申请','AI 能力',
       '允许 Agent 在显式确认后为本人提交一条用印审批申请',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:seal.apply'
FROM rbac_role_permission WHERE permission_code='seal:create'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'seal.apply','Submit one seal usage application',
 'Creates one approval-bound seal usage application owned by the authenticated applicant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["sealType","documentTitle","usageReason","copies"],"properties":{"sealType":{"type":"string","enum":["OFFICIAL","CONTRACT","LEGAL","FINANCE","OTHER"]},"documentTitle":{"type":"string","minLength":1,"maxLength":200},"usageReason":{"type":"string","minLength":1,"maxLength":500},"copies":{"type":"integer","minimum":1,"maximum":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["usageId","status","version","submittedAt"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:7c88a6102e7a8048fcef44592c6ecf28c21bb21e1e4bd3f11c81a91ce4d80632',
 'L1','["seal:create"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
