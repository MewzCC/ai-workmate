INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'notification.mine', 'Query my notifications',
    'Returns notifications owned by the authenticated user in the authenticated tenant.', '1.0.0',
    '{"type":"object","additionalProperties":false,"properties":{"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","type","title","content","read","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"type":{"type":"string","maxLength":40},"title":{"type":"string","maxLength":200},"content":{"type":"string","maxLength":2000},"businessType":{"type":"string","maxLength":40},"read":{"type":"boolean"},"createdAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    'sha256:14131b81768944254704a23d89ff3ee3498936a000ef2d595c1427b501ed13a1',
    'L0', '["notification:read:self"]'::jsonb, 'ALL', 'SELF', 'READ_ONLY_SAFE', 'NONE', 'NONE',
    50, 65536, 15000, 'HASHED_ARGS_RESULT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
