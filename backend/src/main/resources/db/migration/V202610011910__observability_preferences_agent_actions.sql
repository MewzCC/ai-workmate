INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,'AI 能力',permission.description,tenant.id
FROM tenant
CROSS JOIN (VALUES
 ('agent:tool:observability.preferences.update','Agent 调整平台观测图表','允许 Agent 经明确确认后调整当前用户自己的平台观测图表偏好'),
 ('agent:tool:observability.thresholds.update','Agent 调整平台观测视觉阈值','允许 Agent 经明确确认后调整当前用户自己的平台观测视觉阈值')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,permission.code
FROM rbac_role_permission grant_row
CROSS JOIN (VALUES
 ('agent:tool:observability.preferences.update'),
 ('agent:tool:observability.thresholds.update')
) AS permission(code)
WHERE grant_row.permission_code='runtime-log:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'observability.preferences.update','Update my observability charts',
 'Updates the authenticated user''s ordered platform observability chart preferences.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["charts"],"properties":{"charts":{"type":"array","minItems":1,"maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["id","kind","title","mode","content","size","granularity"],"properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{1,64}$"},"kind":{"type":"string","enum":["volume","risk","source","error"]},"title":{"type":"string","maxLength":40},"mode":{"type":"string","enum":["line","area","bar","mixed","donut"]},"content":{"type":"array","maxItems":8,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}},"size":{"type":"string","enum":["normal","wide"]},"granularity":{"type":"string","enum":["auto","hour","day"]}}}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["charts"],"properties":{"charts":{"type":"array","minItems":1,"maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["id","kind","title","mode","content","size","granularity"],"properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{1,64}$"},"kind":{"type":"string","enum":["volume","risk","source","error"]},"title":{"type":"string","maxLength":40},"mode":{"type":"string","enum":["line","area","bar","mixed","donut"]},"content":{"type":"array","maxItems":8,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}},"size":{"type":"string","enum":["normal","wide"]},"granularity":{"type":"string","enum":["auto","hour","day"]}}}}}}'::jsonb,
 'sha256:3d38087e617264d2bf2587fa0090b63e7b7bf9ca9a260359edcf98a7dce44149','L1','["runtime-log:read"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 12,16384,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'observability.thresholds.update','Update my observability visual thresholds',
 'Updates the authenticated user''s personal visual threshold preferences.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"failedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"blockedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"p95DurationMs":{"type":["integer","null"],"minimum":1,"maximum":600000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"properties":{"failedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"blockedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"p95DurationMs":{"type":["integer","null"],"minimum":1,"maximum":600000}}}'::jsonb,
 'sha256:848b2842fe4a3a82da611bdf0ff6531c185eaa23bf095deac369747b79248bd8','L1','["runtime-log:read"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
