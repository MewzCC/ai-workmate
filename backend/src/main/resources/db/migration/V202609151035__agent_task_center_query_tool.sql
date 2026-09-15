INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,permission.module,permission.description,tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:task:read','查询本人 Agent 任务','AI 能力','查询当前用户自己的 Agent 任务摘要'),
 ('agent:tool:agentTask.mine.query','Agent 本人任务查询','AI 能力','允许 Agent 通过受控只读工具查询本人任务摘要'))
 AS permission(code,name,module,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT rp.tenant_id,rp.role_code,permission.code
FROM rbac_role_permission rp CROSS JOIN (VALUES ('agent:task:read'),('agent:tool:agentTask.mine.query')) AS permission(code)
WHERE rp.permission_code='route:ai-tasks'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'agentTask.mine.query','Query my Agent tasks','Returns only the authenticated user''s Agent task summaries without plan, arguments or results.','1.0.0',
'{"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PLANNED","WAITING_CONFIRMATION","QUEUED","RUNNING","SUCCEEDED","FAILED","CANCELLED","EXPIRED"]},"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
'{"type":"object","additionalProperties":false,"required":["records","total","page","size"],"properties":{"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["taskId","pageId","status","riskLevel","planVersion","createdAt","updatedAt"],"properties":{"taskId":{"type":"string","maxLength":80},"pageId":{"type":"string","maxLength":80},"status":{"type":"string","maxLength":40},"riskLevel":{"type":"string","maxLength":8},"planVersion":{"type":"integer","minimum":1},"createdAt":{"type":"string","format":"date-time"},"updatedAt":{"type":"string","format":"date-time"},"finishedAt":{"type":"string","format":"date-time"},"errorCode":{"type":"string","maxLength":80}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
'sha256:1d4697a6ea535d206068ea1b0e6d8605c2c8f7d80686f90602131dde3df87658','L0','["agent:task:read"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
