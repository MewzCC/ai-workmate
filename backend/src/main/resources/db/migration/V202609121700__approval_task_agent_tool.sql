INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:approval.task.query', 'Agent 查询审批中心', 'AI 能力',
       '允许 Agent 按当前用户的租户数据范围只读查询审批任务安全摘要', tenant.id
FROM tenant
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT role_permission.tenant_id,
       role_permission.role_code,
       'agent:tool:approval.task.query'
FROM rbac_role_permission role_permission
WHERE role_permission.permission_code = 'approval:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES (
    NULL, 'approval.task.query', 'Query tenant approval tasks',
    'Returns bounded approval tasks visible to the authenticated actor''s live tenant data scope.', '1.0.0',
    '{"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"keyword":{"type":"string","maxLength":200},"leaveType":{"type":"string","maxLength":40},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["applicationId","applicantName","leaveType","durationDays","status","version","overdue"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"durationDays":{"type":"number","minimum":0.5},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
    'sha256:5c75860e7c13bb66edf25ddc589a41710a16de8a850c4c7fdb0b1df4d805f876',
    'L0', '["approval:read"]'::jsonb, 'ALL', 'TENANT_SCOPED', 'READ_ONLY_SAFE',
    'NONE', 'NONE', 50, 65536, 15000, 'HASHED_ARGS_RESULT', TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
