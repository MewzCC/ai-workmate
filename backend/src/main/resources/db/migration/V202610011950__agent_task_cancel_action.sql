INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:agentTask.cancel','Agent 取消本人任务','AI 能力',
       '允许 Agent 经明确确认后取消当前用户自己的一条可取消任务',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:agentTask.cancel'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='agent:task:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'agentTask.cancel','Cancel my Agent task',
 'Cancels exactly one cancellable Agent task owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"string","minLength":1,"maxLength":80}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["taskId","status","updatedAt"],"properties":{"taskId":{"type":"string","maxLength":80},"status":{"type":"string","const":"CANCELLED"},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:69904eabce7fcee590879e4aef10104ae960cc6141e32b175145f4e95c325ff2','L1',
 '["agent:task:read"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,4096,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
