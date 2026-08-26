INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version,
    parameters_schema, output_schema, schema_hash, risk_level,
    required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy,
    max_result_items, max_result_bytes, timeout_ms, audit_level, enabled
) VALUES (
    NULL,
    'todo.query',
    'Query my approval tasks',
    'Returns approval tasks assigned to the authenticated user in the authenticated tenant.',
    '1.0.0',
    '{"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicationId","applicantName","leaveType","durationHalfDays","status","version","overdue"],"properties":{"id":{"type":"integer","minimum":1},"applicationId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"durationHalfDays":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    'sha256:5587d07883805ecb5810979e984dc33044c68898a9e9aeed4d07c4f3a9793c69',
    'L0',
    '["todo:read"]'::jsonb,
    'ALL',
    'ASSIGNED_TO_SELF',
    'READ_ONLY_SAFE',
    'NONE',
    'NONE',
    50,
    65536,
    15000,
    'HASHED_ARGS_RESULT',
    TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
