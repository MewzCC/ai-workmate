ALTER TABLE leave_application
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_leave_agent_operation
    ON leave_application(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

ALTER TABLE agent_tool DROP CONSTRAINT IF EXISTS chk_agent_tool_code;
ALTER TABLE agent_tool ADD CONSTRAINT chk_agent_tool_code
    CHECK (code ~ '^[a-z][A-Za-z0-9]*(\.[a-z][A-Za-z0-9]*)+$');

INSERT INTO agent_tool(
    tenant_id, code, name, description, handler_version,
    parameters_schema, output_schema, schema_hash,
    risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items,
    max_result_bytes, timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'leave.createDraft', 'Create my leave draft',
    'Creates exactly one draft owned by the authenticated user in the authenticated tenant.',
    '1.0.0',
    '{"type":"object","additionalProperties":false,"required":["leaveType","startDate","startPeriod","endDate","endPeriod","reason"],"properties":{"leaveType":{"type":"string","enum":["ANNUAL","PERSONAL","SICK","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","COMPENSATORY","OTHER"]},"approverUserId":{"type":"integer","minimum":1},"startDate":{"type":"string","format":"date","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","format":"date","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}'::jsonb,
    'sha256:cf8365a51babc334f5a03a90739a97f18b105dd034ab8ae6fb942934795f6dfa',
    'L1', '["leave:create"]'::jsonb,
    'ALL', 'SELF', 'BUSINESS_IDEMPOTENT', 'SINGLE_WRITE', 'EXPLICIT',
    1, 16384, 15000, 'FULL_WRITE_AUDIT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    handler_version = EXCLUDED.handler_version,
    parameters_schema = EXCLUDED.parameters_schema,
    output_schema = EXCLUDED.output_schema,
    schema_hash = EXCLUDED.schema_hash,
    risk_level = EXCLUDED.risk_level,
    required_permissions = EXCLUDED.required_permissions,
    permission_mode = EXCLUDED.permission_mode,
    data_scope_policy = EXCLUDED.data_scope_policy,
    retry_policy = EXCLUDED.retry_policy,
    side_effect = EXCLUDED.side_effect,
    confirmation_policy = EXCLUDED.confirmation_policy,
    max_result_items = EXCLUDED.max_result_items,
    max_result_bytes = EXCLUDED.max_result_bytes,
    timeout_ms = EXCLUDED.timeout_ms,
    audit_level = EXCLUDED.audit_level,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;
