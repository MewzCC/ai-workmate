CREATE TABLE IF NOT EXISTS seal_usage_operation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    usage_id BIGINT NOT NULL REFERENCES seal_usage(id) ON DELETE CASCADE,
    operator_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL,
    agent_operation_key VARCHAR(128) NOT NULL,
    source_version INTEGER NOT NULL,
    result_version INTEGER NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    actual_copies INTEGER,
    remark VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_seal_usage_operation_type
        CHECK (operation_type IN ('USE', 'RETURN')),
    CONSTRAINT ck_seal_usage_operation_versions
        CHECK (source_version >= 0 AND result_version = source_version + 1),
    CONSTRAINT ck_seal_usage_operation_copies
        CHECK (actual_copies IS NULL OR actual_copies >= 1),
    CONSTRAINT ux_seal_usage_agent_operation_key
        UNIQUE (tenant_id, operator_user_id, agent_operation_key)
);

CREATE INDEX IF NOT EXISTS idx_seal_usage_operation_usage
    ON seal_usage_operation(tenant_id, usage_id, occurred_at DESC);

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:seal.registerUse','Agent 登记实际用印','AI 能力',
       '允许 Agent 在二次确认后登记本人一条已审批申请的实际用印',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:seal.registerUse'
FROM rbac_role_permission WHERE permission_code='seal:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'seal.registerUse','Register one approved seal use',
 'Registers actual use for one approved seal application owned by the authenticated applicant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["usageId","version","actualCopies"],"properties":{"usageId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"actualCopies":{"type":"integer","minimum":1,"maximum":1000},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["usageId","status","version","actualCopies","usedAt"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"USED"},"version":{"type":"integer","minimum":1},"actualCopies":{"type":"integer","minimum":1,"maximum":1000},"usedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:36cb2a0464946f14877a70e591e8643fba0a01d40e5b0348dc337f787e60b8e0',
 'L2','["seal:register"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','SECONDARY',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
