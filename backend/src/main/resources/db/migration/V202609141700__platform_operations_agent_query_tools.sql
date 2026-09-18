INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'integration:endpoint:read','读取接口联调配置','平台能力','读取租户内受控接口的非敏感元数据',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'integration:endpoint:read' FROM rbac_role_permission WHERE permission_code='route:api-center' ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'page-action:read','读取页面操作策略','平台能力','读取代码拥有的页面操作目录和租户启停状态',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'page-action:read' FROM rbac_role_permission WHERE permission_code='route:page-actions' ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:'||tool_code,'Agent 平台运维查询','AI 能力','允许 Agent 通过受控只读工具查询平台运维安全摘要',id
FROM tenant CROSS JOIN (VALUES ('integration.endpoint.query'),('pageAction.query'),('runtimeLog.query'),('sandboxReplay.query')) AS tools(tool_code)
WHERE code='DEFAULT' ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT rp.tenant_id,rp.role_code,'agent:tool:'||mapping.tool_code
FROM rbac_role_permission rp JOIN (VALUES
 ('integration:endpoint:read','integration.endpoint.query'),
 ('page-action:read','pageAction.query'),
 ('runtime-log:read','runtimeLog.query'),
 ('integration:replay:read','sandboxReplay.query')) AS mapping(base_permission,tool_code)
 ON rp.permission_code=mapping.base_permission ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'integration.endpoint.query','Query integration endpoints','Returns controlled endpoint metadata without request templates or response payloads.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"endpointId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","ACTIVE","DISABLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status","version"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":200},"upstreamCode":{"type":"string","maxLength":80},"method":{"type":"string","maxLength":10},"relativePath":{"type":"string","maxLength":500},"description":{"type":"string","maxLength":1000},"status":{"type":"string","maxLength":40},"version":{"type":"integer"},"updatedAt":{"type":"string","format":"date-time"},"canManage":{"type":"boolean"},"canExecute":{"type":"boolean"},"allowedTransitions":{"type":"array","maxItems":50,"items":{"type":"string","maxLength":120}}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:77b6949a8aca42cdd8c7776e51e64e2fa4bf52c64bb4d950e5d61e2c19eadd22','L0','["integration:endpoint:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'pageAction.query','Query page actions','Returns the code-owned page action catalog and tenant enablement state.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"targetPageId":{"type":"string","maxLength":80},"enabled":{"type":"boolean"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["pageId","toolCode","riskLevel","enabled","version"],"properties":{"pageId":{"type":"string","maxLength":80},"toolCode":{"type":"string","maxLength":120},"name":{"type":"string","maxLength":200},"description":{"type":"string","maxLength":1000},"riskLevel":{"type":"string","maxLength":10},"sideEffect":{"type":"string","maxLength":40},"confirmationPolicy":{"type":"string","maxLength":40},"requiredPermissions":{"type":"array","maxItems":50,"items":{"type":"string","maxLength":120}},"enabled":{"type":"boolean"},"explicitlyConfigured":{"type":"boolean"},"version":{"type":"integer"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:7f6c91f43b7a3416e8824b064421ec1be233c0a952fed1b068dc255c8e9cdd96','L0','["page-action:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'runtimeLog.query','Query runtime logs','Returns bounded operational metadata without payload previews or fingerprints.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"source":{"type":"string","enum":["INTEGRATION","AGENT"]},"recordId":{"type":"integer","minimum":1},"outcome":{"type":"string","enum":["RUNNING","SUCCEEDED","REJECTED","FAILED","TIMED_OUT","RESULT_INVALID"]},"keyword":{"type":"string","maxLength":200},"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["recordId","source"]},{"not":{"required":["recordId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["source","id","outcome"],"properties":{"source":{"type":"string","maxLength":20},"id":{"type":"integer","minimum":1},"referenceCode":{"type":"string","maxLength":160},"operation":{"type":"string","maxLength":160},"outcome":{"type":"string","maxLength":40},"decision":{"type":"string","maxLength":80},"statusCode":{"type":"integer"},"durationMs":{"type":"integer"},"operatorLabel":{"type":"string","maxLength":120},"errorCode":{"type":"string","maxLength":120},"handlerInvoked":{"type":"boolean"},"resultBytes":{"type":"integer"},"attempt":{"type":"integer"},"startedAt":{"type":"string","format":"date-time"},"completedAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:d79d49ccc330312907ff310bb96264640e938dbedd5df7c5054fe93357cadcac','L0','["runtime-log:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'sandboxReplay.query','Query sandbox replays','Returns replay outcomes without baseline or response payload content.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"replayId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["RUNNING","SUCCESS","FAILED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","sourceInvocationId","endpointCode","status"],"properties":{"id":{"type":"integer","minimum":1},"sourceInvocationId":{"type":"integer","minimum":1},"endpointCode":{"type":"string","maxLength":80},"endpointName":{"type":"string","maxLength":200},"method":{"type":"string","maxLength":10},"relativePath":{"type":"string","maxLength":500},"baselineOutcome":{"type":"string","maxLength":40},"baselineHttpStatus":{"type":"integer"},"status":{"type":"string","maxLength":40},"replayHttpStatus":{"type":"integer"},"replayDurationMs":{"type":"integer"},"replayErrorCode":{"type":"string","maxLength":120},"comparisonResult":{"type":"string","maxLength":40},"requestedByLabel":{"type":"string","maxLength":120},"startedAt":{"type":"string","format":"date-time"},"completedAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:733b65aba2e3dc720f2d17d6af7be1ea434b93fc7f219c9a551fd4f08e551978','L0','["integration:replay:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
