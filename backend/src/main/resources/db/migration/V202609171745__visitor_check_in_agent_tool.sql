CREATE TABLE IF NOT EXISTS visitor_visit_operation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    booking_id BIGINT NOT NULL REFERENCES visitor_booking(id) ON DELETE CASCADE,
    operator_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL,
    agent_operation_key VARCHAR(128) NOT NULL,
    source_version INTEGER NOT NULL,
    result_version INTEGER NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    remark VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_visitor_visit_operation_type
        CHECK (operation_type IN ('CHECK_IN', 'ARRIVE', 'LEAVE', 'NO_SHOW')),
    CONSTRAINT ck_visitor_visit_operation_versions
        CHECK (source_version >= 0 AND result_version = source_version + 1),
    CONSTRAINT ux_visitor_visit_agent_key
        UNIQUE (tenant_id, operator_user_id, agent_operation_key)
);

CREATE INDEX IF NOT EXISTS idx_visitor_visit_operation_booking
    ON visitor_visit_operation(tenant_id, booking_id, occurred_at DESC);

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:visitor.checkIn','Agent 登记访客签到','AI 能力',
       '允许 Agent 在显式确认后登记本人申请或本人接待的一条已审批访客签到',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:visitor.checkIn'
FROM rbac_role_permission WHERE permission_code='visitor:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.checkIn','Check in one approved visitor',
 'Checks in one approved visitor application related to the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version","occurredAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","const":"CHECKED_IN"},"version":{"type":"integer","minimum":1},"occurredAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:9c88700b29e31bc4bf30fb868ae13d46a7b17e977a0331b9d0d38e6ad88ae98c',
 'L1','["visitor:register"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
