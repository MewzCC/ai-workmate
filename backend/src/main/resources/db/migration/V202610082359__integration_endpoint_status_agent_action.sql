INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:integration.endpoint.updateStatus','Agent 启停接口端点','AI 能力',
       '允许 Agent 经二次确认、版本和上游可用性校验后启用或停用本租户单个接口端点且不执行端点请求',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:integration.endpoint.updateStatus'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='integration:endpoint:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'integration.endpoint.updateStatus','Update one integration endpoint status',
 'Applies one allowed status transition to one tenant-scoped endpoint with optimistic locking.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["endpointId","version","status"],"properties":{"endpointId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["ACTIVE","DISABLED"]}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["endpointId","code","status","version","updatedAt"],"properties":{"endpointId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:48dd8ca12531584d28decdd30c84948c59e68fb08db1d32b1e393c517cdadba8','L2',
 '["integration:endpoint:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',1,4096,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
