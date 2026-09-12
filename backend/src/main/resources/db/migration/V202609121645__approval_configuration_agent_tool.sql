INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:approval.configuration.query', 'Agent 查询审批配置', 'AI 能力',
       '允许 Agent 只读查询当前租户的审批表单、流程与规则安全摘要', tenant.id
FROM tenant
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT role_permission.tenant_id,
       role_permission.role_code,
       'agent:tool:approval.configuration.query'
FROM rbac_role_permission role_permission
WHERE role_permission.permission_code = 'approval:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'approval.configuration.query', 'Query approval configuration',
    'Returns bounded approval forms, processes or rules from the authenticated tenant.', '1.0.0',
    '{"type":"object","additionalProperties":false,"required":["resource"],"properties":{"resource":{"type":"string","enum":["FORM","PROCESS","RULE"]},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","ENABLED","DISABLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","resource","key","name","status","version","updatedAt"],"properties":{"id":{"type":"integer","minimum":1},"resource":{"type":"string","enum":["FORM","PROCESS","RULE"]},"key":{"type":"string","maxLength":120},"name":{"type":"string","maxLength":200},"description":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["DRAFT","ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0},"formName":{"type":"string","maxLength":200},"ruleType":{"type":"string","maxLength":80},"priority":{"type":"integer"},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    'sha256:2e459355b6911204453ea0dc07f403477514a5df1fba877d52c29e48bf08fb22',
    'L0', '["approval:read"]'::jsonb, 'ALL', 'TENANT_SCOPED', 'READ_ONLY_SAFE',
    'NONE', 'NONE', 50, 65536, 15000, 'HASHED_ARGS_RESULT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
