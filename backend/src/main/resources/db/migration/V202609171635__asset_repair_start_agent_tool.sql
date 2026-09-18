INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'asset:repair','登记资产维修','行政资产','允许将一件空闲资产登记为维修中',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'asset:repair'
FROM rbac_role_permission WHERE permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.repair.start','Agent 登记资产维修','AI 能力',
       '允许 Agent 在显式确认后将一件空闲资产登记为维修中，仍受实时资产权限和状态校验',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.repair.start'
FROM rbac_role_permission WHERE permission_code='asset:repair'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.repair.start','Register one asset repair',
 'Moves one idle tenant asset into repairing status with a required fault reason.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version","reason"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"REPAIRING"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:e890d896f8792d1e24271ab93b2cde6ce4585fdd262c20204660fddd2d95368f',
 'L1','["asset:repair"]'::jsonb,'ALL','TENANT_SCOPED','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
