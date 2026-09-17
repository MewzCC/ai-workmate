ALTER TABLE asset_operation
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_version INTEGER,
    ADD COLUMN IF NOT EXISTS result_version INTEGER;

ALTER TABLE asset_operation
    DROP CONSTRAINT IF EXISTS ck_asset_operation_versions;

ALTER TABLE asset_operation
    ADD CONSTRAINT ck_asset_operation_versions CHECK (
        (source_version IS NULL AND result_version IS NULL)
        OR (source_version >= 0 AND result_version = source_version + 1)
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_operation_agent_key
    ON asset_operation(tenant_id, operator_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'asset:claim','办理资产领用','行政资产','允许将一件空闲资产分配给同租户有效员工',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'asset:claim'
FROM rbac_role_permission WHERE permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.claim','Agent 办理资产领用','AI 能力',
       '允许 Agent 在显式确认后办理一件资产领用，仍受实时资产权限和状态校验',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.claim'
FROM rbac_role_permission WHERE permission_code='asset:claim'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.claim','Assign one asset to an employee',
 'Assigns one idle tenant asset to one active employee selected by the authenticated operator.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","employeeId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"employeeId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"IN_USE"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:70e57baaa8ec2003241bf090427125dad1ae0da3537813e62ce88d2615a2f9b5',
 'L1','["asset:claim"]'::jsonb,'ALL','TENANT_SCOPED','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
