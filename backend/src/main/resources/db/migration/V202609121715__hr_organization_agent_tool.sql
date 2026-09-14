INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:hr.organization.query', 'Agent 查询组织架构', 'AI 能力',
       '允许 Agent 按当前用户实时数据范围只读查询组织、岗位与员工安全摘要', tenant.id
FROM tenant
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT role_permission.tenant_id, role_permission.role_code,
       'agent:tool:hr.organization.query'
FROM rbac_role_permission role_permission
WHERE role_permission.permission_code = 'hr:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'hr.organization.query', 'Query visible organization',
    'Returns bounded departments, positions and employees visible in the authenticated actor''s data scope.', '1.0.0',
    '{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"limit":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["departments","positions","employees"],"properties":{"departments":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":120},"parentId":{"type":"integer","minimum":1},"status":{"type":"integer","minimum":0,"maximum":1}}}},"positions":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":120},"status":{"type":"integer","minimum":0,"maximum":1}}}},"employees":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","name","role","status"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"role":{"type":"string","maxLength":80},"status":{"type":"integer","minimum":0,"maximum":1},"departmentId":{"type":"integer","minimum":1},"positionId":{"type":"integer","minimum":1},"approverName":{"type":"string","maxLength":120}}}}}}'::jsonb,
    'sha256:abfc7576bc4f76ce773f2eb630548ea7e051e9931ab674660fdcfdaec83fdd9d',
    'L0', '["hr:read"]'::jsonb, 'ALL', 'TENANT_SCOPED', 'READ_ONLY_SAFE',
    'NONE', 'NONE', 50, 131072, 15000, 'HASHED_ARGS_RESULT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
