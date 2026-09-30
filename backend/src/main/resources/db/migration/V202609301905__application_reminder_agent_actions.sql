INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, '流程审批', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('leave:remind','催办请假申请','允许申请人催办本人一条待审批请假申请'),
 ('approval:remind','催办通用审批','允许申请人催办本人一条待审批通用申请')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'leave:remind'
FROM rbac_role_permission source
WHERE source.permission_code='leave:create'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'approval:remind'
FROM rbac_role_permission source
WHERE source.permission_code='approval:submit'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:tool:leave.remind','Agent 催办请假申请','允许 Agent 在显式确认后催办本人一条待审批请假申请'),
 ('agent:tool:approval.application.remind','Agent 催办通用审批','允许 Agent 在显式确认后催办本人一条待审批通用申请')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:leave.remind'
FROM rbac_role_permission source
WHERE source.permission_code='leave:remind'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:approval.application.remind'
FROM rbac_role_permission source
WHERE source.permission_code='approval:remind'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'leave.remind','Remind one leave approver','Sends one rate-limited reminder for an owned pending leave application.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:8c743332d270bda67ec2f2575f2d2a32b2b1cfaf62e4113601cfdadbc57a53a5','L1','["leave:remind"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,4096,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'approval.application.remind','Remind one approval assignee','Sends one rate-limited reminder for an owned pending generic approval application.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:edbe7ad52b50a98e339f5e7cf83b572c26d3b71ddccb1f50aa11496fbed80426','L1','["approval:remind"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
