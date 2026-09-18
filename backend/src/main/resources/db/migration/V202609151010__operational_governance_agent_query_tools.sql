INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:'||tool_code,'Agent 运行治理查询','AI 能力','允许 Agent 通过受控只读工具查询运行治理摘要',id
FROM tenant CROSS JOIN (VALUES ('audit.query'),('tenantConfiguration.query'),('dictionary.query'),('systemCapability.query')) AS tools(tool_code)
WHERE code='DEFAULT' ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT rp.tenant_id,rp.role_code,'agent:tool:'||mapping.tool_code
FROM rbac_role_permission rp JOIN (VALUES
 ('audit:read','audit.query'),
 ('tenant:config:manage','tenantConfiguration.query'),
 ('dictionary:manage','dictionary.query'),
 ('access:manage','systemCapability.query')) AS mapping(base_permission,tool_code)
 ON rp.permission_code=mapping.base_permission ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'audit.query','Query audit records','Returns tenant audit metadata without actor identifiers, resource identifiers, traces or summaries.','1.0.0',
'{"type":"object","additionalProperties":false,"properties":{"action":{"type":"string","maxLength":100},"resourceType":{"type":"string","maxLength":100},"result":{"type":"string","maxLength":40},"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
'{"type":"object","additionalProperties":false,"required":["records","total","page","size"],"properties":{"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","resourceType","action","result","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"resourceType":{"type":"string","maxLength":100},"action":{"type":"string","maxLength":100},"result":{"type":"string","maxLength":40},"createdAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
'sha256:c20374b208d69def5365f321daa3c9d80c8c7a54ed7ab0a1b0cd10ade022ab04','L0','["audit:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'tenantConfiguration.query','Query tenant configuration','Returns the authorized tenant business and security policy summary.','1.0.0',
'{"type":"object","additionalProperties":false,"properties":{}}'::jsonb,
'{"type":"object","additionalProperties":false,"required":["tenantName","locale","timezone","fiscalYearStartMonth","features","defaultApprovalDays","expenseCurrency","passwordMinLength","sessionTimeoutMinutes","version","updatedAt"],"properties":{"tenantName":{"type":"string","maxLength":160},"tenantShortName":{"type":"string","maxLength":80},"locale":{"type":"string","maxLength":20},"timezone":{"type":"string","maxLength":80},"fiscalYearStartMonth":{"type":"integer","minimum":1,"maximum":12},"features":{"type":"object","additionalProperties":false,"required":["approval","attendance","asset","meeting","visitor","seal"],"properties":{"approval":{"type":"boolean"},"attendance":{"type":"boolean"},"asset":{"type":"boolean"},"meeting":{"type":"boolean"},"visitor":{"type":"boolean"},"seal":{"type":"boolean"}}},"defaultApprovalDays":{"type":"integer","minimum":1,"maximum":365},"expenseCurrency":{"type":"string","maxLength":10},"passwordMinLength":{"type":"integer","minimum":1,"maximum":128},"sessionTimeoutMinutes":{"type":"integer","minimum":1,"maximum":10080},"version":{"type":"integer","minimum":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
'sha256:884afa367781930d66fdafc2b64205710c17b546099610be43be6e960531cba6','L0','["tenant:config:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'dictionary.query','Query dictionaries','Returns dictionary type summaries without internal identifiers or item values.','1.0.0',
'{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":100},"status":{"type":"string","enum":["ACTIVE","DISABLED"]}}}'::jsonb,
'{"type":"object","additionalProperties":false,"required":["records","canManage"],"properties":{"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["code","name","status","sortOrder","itemCount","activeItemCount","version","updatedAt"],"properties":{"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":160},"description":{"type":"string","maxLength":500},"status":{"type":"string","maxLength":40},"sortOrder":{"type":"integer","minimum":0},"itemCount":{"type":"integer","minimum":0},"activeItemCount":{"type":"integer","minimum":0},"version":{"type":"integer","minimum":0},"updatedAt":{"type":"string","format":"date-time"}}}},"canManage":{"type":"boolean"}}}'::jsonb,
'sha256:d4cc084b963a3e48f1a63c8ab6b988c1f4a8ce361db6b4a74d28867cb821ff92','L0','["dictionary:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'systemCapability.query','Query system capabilities','Returns availability flags without credentials, endpoints, connection strings or exception details.','1.0.0',
'{"type":"object","additionalProperties":false,"properties":{}}'::jsonb,
'{"type":"object","additionalProperties":false,"required":["checkedAt","capabilities"],"properties":{"checkedAt":{"type":"string","format":"date-time"},"capabilities":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["code","enabled","available","status"],"properties":{"code":{"type":"string","maxLength":40},"enabled":{"type":"boolean"},"available":{"type":"boolean"},"status":{"type":"string","maxLength":40}}}}}}'::jsonb,
'sha256:4e80069fa10fe0a961e030bd54c85a87f69238ed9ddab94675085576bcefae88','L0','["access:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
