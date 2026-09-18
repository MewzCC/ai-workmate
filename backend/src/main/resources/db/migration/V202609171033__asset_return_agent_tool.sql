INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'asset:return','办理资产归还','行政资产','允许将一件使用中的资产归还到空闲状态',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'asset:return'
FROM rbac_role_permission WHERE permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.return','Agent 办理资产归还','AI 能力',
       '允许 Agent 在显式确认后归还一件资产，仍受实时资产权限和状态校验',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.return'
FROM rbac_role_permission WHERE permission_code='asset:return'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.return','Return one assigned asset',
 'Returns one in-use tenant asset to the idle pool for the authenticated operator.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"IDLE"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:2370eb1e5d8a544c52f331ccd8deaff7865ee1db276844e2237f63496ee95f3e',
 'L1','["asset:return"]'::jsonb,'ALL','TENANT_SCOPED','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
