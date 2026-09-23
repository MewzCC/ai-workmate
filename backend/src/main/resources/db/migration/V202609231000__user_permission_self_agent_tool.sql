INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,permission.module,permission.description,tenant.id
FROM tenant CROSS JOIN (VALUES
 ('user-permission:read:self','查看本人实时权限','AI 能力','查看当前用户自己的实时角色、数据范围和权限码'),
 ('agent:tool:userPermission.mine.query','Agent 本人权限查询','AI 能力','允许 Agent 通过受控只读工具查询当前用户自己的实时权限'))
 AS permission(code,name,module,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
 name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT rp.tenant_id,rp.role_code,permission.code
FROM rbac_role_permission rp
CROSS JOIN (VALUES
 ('user-permission:read:self'),
 ('agent:tool:userPermission.mine.query')) AS permission(code)
WHERE rp.permission_code IN ('route:dashboard','route:ai-workspace')
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'userPermission.mine.query','Query my live permissions',
 'Returns only the authenticated user''s current roles, data scopes and bounded permission codes.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":80},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["primaryRole","roles","dataScopes","permissionVersion","permissions","total","page","size"],"properties":{"primaryRole":{"type":"string","maxLength":80},"roles":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":80}},"dataScopes":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":80}},"permissionVersion":{"type":"integer","minimum":0},"permissions":{"type":"array","maxItems":50,"items":{"type":"string","maxLength":120}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:3a7a57d10e446b40d09a32d49c9a94bd017872d9e2f823b1890802d6f1e11128',
 'L0','["user-permission:read:self"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',
 50,65536,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
