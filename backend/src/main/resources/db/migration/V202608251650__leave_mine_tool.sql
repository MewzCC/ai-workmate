INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version,
    parameters_schema, output_schema, schema_hash, risk_level,
    required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy,
    max_result_items, max_result_bytes, timeout_ms, audit_level, enabled
) VALUES (
    NULL,
    'leave.mine',
    'Query my leave applications',
    'Returns leave applications owned by the authenticated user in the authenticated tenant.',
    '1.0.0',
    '{"type":"object","additionalProperties":false,"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["applicationId"],"not":{"anyOf":[{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["applicationId"]}}]}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","leaveType","startDate","startPeriod","endDate","endPeriod","durationHalfDays","durationDays","reason","status","version","createdAt","updatedAt"],"properties":{"id":{"type":"integer","minimum":1},"approverName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"startDate":{"type":"string","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"durationHalfDays":{"type":"integer","minimum":1},"durationDays":{"type":"number","minimum":0.5},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    'sha256:9b1d1ce3ec13c9c67f969c939c86eb4ab87659bee011ef2d32eded8a40bd26bf',
    'L0',
    '["leave:read:self"]'::jsonb,
    'ALL',
    'SELF',
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
