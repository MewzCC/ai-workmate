INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:integration.endpoint.createDraft','Agent 创建接口端点草稿','AI 能力',
       '允许 Agent 经二次确认后基于服务端已注册上游创建单个接口端点草稿且不执行网络调用',tenant.id
FROM tenant
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:integration.endpoint.createDraft'
FROM rbac_role_permission grant_row
WHERE grant_row.permission_code='integration:endpoint:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'integration.endpoint.createDraft','Create one integration endpoint draft',
 'Creates one draft endpoint against a server-registered upstream without executing it.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name","upstreamCode","method","relativePath"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$","maxLength":64},"name":{"type":"string","minLength":1,"maxLength":160},"upstreamCode":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,39}$","maxLength":40},"method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE"]},"relativePath":{"type":"string","minLength":1,"maxLength":500},"requestTemplate":{"type":"string","maxLength":16000},"description":{"type":"string","maxLength":2000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["endpointId","code","name","upstreamCode","method","relativePath","status","version","updatedAt"],"properties":{"endpointId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":160},"upstreamCode":{"type":"string","maxLength":40},"method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE"]},"relativePath":{"type":"string","maxLength":500},"description":{"type":["string","null"],"maxLength":2000},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:0bd660271acaffa2206989975ab46244c231f6c1d3794b4828707b1211fd19d9','L2',
 '["integration:endpoint:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',1,8192,10000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
