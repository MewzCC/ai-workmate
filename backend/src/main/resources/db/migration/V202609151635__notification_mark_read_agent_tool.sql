INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:notification.markRead', 'Agent 标记本人消息已读', 'AI 能力',
       '允许 Agent 将当前用户拥有的一条消息标记为已读，仍需显式确认', tenant.id
FROM tenant
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT tenant_id, role_code, 'agent:tool:notification.markRead'
FROM rbac_role_permission
WHERE permission_code = 'notification:read:self'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
)
VALUES (
    NULL, 'notification.markRead', 'Mark my notification as read',
    'Marks exactly one notification owned by the authenticated user as read.', '1.0.0',
    '{"type":"object","additionalProperties":false,"required":["notificationId"],"properties":{"notificationId":{"type":"integer","minimum":1}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["notificationId","read"],"properties":{"notificationId":{"type":"integer","minimum":1},"read":{"type":"boolean","const":true}}}'::jsonb,
    'sha256:b46cdf75d92a2dbf816572096b8b1eb184ce2040ed702150a6e371d08ba1b487',
    'L1', '["notification:read:self"]'::jsonb, 'ALL', 'SELF',
    'BUSINESS_IDEMPOTENT', 'SINGLE_WRITE', 'EXPLICIT', 1, 4096, 5000,
    'FULL_WRITE_AUDIT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
