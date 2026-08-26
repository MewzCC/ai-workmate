INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'knowledge.search', 'Search authorized knowledge',
    'Searches only ready knowledge chunks owned by the authenticated user and tenant.', '1.0.0',
    '{"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":1000},"topK":{"type":"integer","minimum":1,"maximum":10},"minScore":{"type":"number","minimum":0.0,"maximum":1.0}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","untrustedContent","usagePolicy"],"properties":{"items":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["content","score","matchType","citation"],"properties":{"content":{"type":"string","maxLength":12000},"score":{"type":"number","minimum":-1.0,"maximum":1.0},"matchType":{"type":"string","enum":["DENSE","SPARSE","HYBRID"]},"citation":{"type":"object","additionalProperties":false,"required":["documentId","chunkId","filename","chunkIndex"],"properties":{"documentId":{"type":"integer","minimum":1},"chunkId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"chunkIndex":{"type":"integer","minimum":0}}}}}},"untrustedContent":{"type":"boolean","const":true},"usagePolicy":{"type":"string","const":"DISPLAY_OR_SUMMARIZE_ONLY"}}}'::jsonb,
    'sha256:3f65a4f7a015f1ca051fd5fad0776ed4aeed9228cf5b1059aa63ee84dab9d30f',
    'L0', '["knowledge:search"]'::jsonb, 'ALL', 'SELF', 'READ_ONLY_SAFE', 'NONE', 'NONE',
    10, 131072, 15000, 'HASHED_ARGS_RESULT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
