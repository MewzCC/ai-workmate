INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:dashboard.overview.query','Agent 查询本人驾驶舱','AI 能力',
       '允许 Agent 读取本人驾驶舱指标与有界趋势，不读取租户汇总或敏感明细',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:dashboard.overview.query'
FROM rbac_role_permission WHERE permission_code='dashboard:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'dashboard.overview.query','Query my dashboard overview',
 'Reads personal dashboard aggregates without tenant totals or sensitive record details.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"days":{"type":"integer","minimum":1,"maximum":30}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["generatedAt","days","metrics","trends","businessDistribution","distributionTruncated"],"properties":{"generatedAt":{"type":"string","format":"date-time"},"days":{"type":"integer","minimum":1,"maximum":30},"metrics":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,"required":["code","value"],"properties":{"code":{"type":"string","enum":["PENDING_TODOS","OVERDUE_TODOS","MY_APPLICATIONS","UNREAD_MESSAGES"]},"value":{"type":"integer","minimum":0}}}},"trends":{"type":"array","maxItems":30,"items":{"type":"object","additionalProperties":false,"required":["date","submitted","completed"],"properties":{"date":{"type":"string","format":"date"},"submitted":{"type":"integer","minimum":0},"completed":{"type":"integer","minimum":0}}}},"businessDistribution":{"type":"array","maxItems":16,"items":{"type":"object","additionalProperties":false,"required":["businessType","value"],"properties":{"businessType":{"type":"string","maxLength":80},"value":{"type":"integer","minimum":0}}}},"distributionTruncated":{"type":"boolean"}}}'::jsonb,
 'sha256:e27a8ab9413f2ef431e93ede1a0a0055dc3c683b3cc71f648c7c8bd383135282','L0','["dashboard:read"]'::jsonb,
 'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,32768,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
