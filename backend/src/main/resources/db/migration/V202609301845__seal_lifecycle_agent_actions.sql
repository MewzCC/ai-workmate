INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:tool:seal.withdraw','Agent 撤回用印申请','允许 Agent 在显式确认后撤回本人一条待审批用印申请'),
 ('agent:tool:seal.return','Agent 登记印章归还','允许 Agent 在显式确认后为一条有权操作的已用印记录登记归还')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:seal.withdraw'
FROM rbac_role_permission source
WHERE source.permission_code='seal:withdraw'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:seal.return'
FROM rbac_role_permission source
WHERE source.permission_code='seal:register'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'seal.withdraw','Withdraw one pending seal application','Withdraws one pending seal application owned by the authenticated applicant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["usageId","version"],"properties":{"usageId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["usageId","status","version"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:48bf01914cebaa352fe82a1159d420740b010fb6eec110f0ef7525f764cac005','L1','["seal:withdraw"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'seal.return','Register one seal return','Registers return for one used seal application visible to the authenticated registrar.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["usageId","version"],"properties":{"usageId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["usageId","status","version"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"RETURNED"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:943c6c284623e111a9f4c8fcabe36d1585156d0f4bea7e5cff8d0cd5e6afa0fd','L1','["seal:register"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
