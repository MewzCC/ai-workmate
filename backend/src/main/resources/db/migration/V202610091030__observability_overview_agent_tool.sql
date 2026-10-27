INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:observability.overview.query','Agent 查询平台观测聚合','AI 能力',
       '允许 Agent 读取当前租户平台观测聚合与有界完整趋势，不输出身份或请求内容',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:observability.overview.query'
FROM rbac_role_permission WHERE permission_code='runtime-log:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'observability.overview.query','Query platform observability',
 'Reads current-tenant aggregates and bounded complete timeline without identities or payloads.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"range":{"type":"string","enum":["24h","7d","30d"]}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["from","to","interval","stats","timeline","sources","errorCodes","categoriesTruncated"],"properties":{"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"interval":{"type":"string","enum":["hour","day"]},"stats":{"type":"object","additionalProperties":false,"required":["total","succeeded","failed","blocked"],"properties":{"total":{"type":"integer","minimum":0},"succeeded":{"type":"integer","minimum":0},"failed":{"type":"integer","minimum":0},"blocked":{"type":"integer","minimum":0},"averageDurationMs":{"type":"integer","minimum":0}}},"p95DurationMs":{"type":"integer","minimum":0},"timeline":{"type":"array","maxItems":31,"items":{"type":"object","additionalProperties":false,"required":["bucket","total","failed","blocked"],"properties":{"bucket":{"type":"string","format":"date-time"},"total":{"type":"integer","minimum":0},"failed":{"type":"integer","minimum":0},"blocked":{"type":"integer","minimum":0}}}},"sources":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["code","total"],"properties":{"code":{"type":"string","maxLength":120},"total":{"type":"integer","minimum":0}}}},"errorCodes":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["code","total"],"properties":{"code":{"type":"string","maxLength":120},"total":{"type":"integer","minimum":0}}}},"categoriesTruncated":{"type":"boolean"}}}'::jsonb,
 'sha256:e2b4272f1feb67c26f738a8ed15c84cf52eee23f58c2df2cfa87953d484f171b','L0','["runtime-log:read"]'::jsonb,
 'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,32768,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
