INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:tool:visitor.withdraw','Agent 撤回访客预约','允许 Agent 在显式确认后撤回本人一条待审批访客预约'),
 ('agent:tool:visitor.noShow','Agent 标记访客失约','允许 Agent 在显式确认后将一条已过预约时间的访客记录标记为失约')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:visitor.withdraw'
FROM rbac_role_permission source
WHERE source.permission_code='visitor:withdraw'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:visitor.noShow'
FROM rbac_role_permission source
WHERE source.permission_code='visitor:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'visitor.withdraw','Withdraw one pending visitor booking','Withdraws one pending visitor booking owned by the authenticated applicant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:db8d511f72e6025c495c596e4160a7562ee4f82b794bfd6eb52e75561333bbdc','L1','["visitor:withdraw"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'visitor.noShow','Mark one approved visitor as no-show','Marks one approved visitor booking related to the authenticated registrar as no-show.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["bookingId","version"],"properties":{"bookingId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["bookingId","status","version","occurredAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","const":"NO_SHOW"},"version":{"type":"integer","minimum":1},"occurredAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:43572cea8f475a40e1851407cb017eb1c715e16e3700ec7a29446a37753a807d','L1','["visitor:register"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
