INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.query','Agent 查询资产台账','AI 能力',
       '允许 Agent 按实时租户权限只读查询资产台账安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.query'
FROM rbac_role_permission WHERE permission_code='assets:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.query','Query tenant assets',
 'Returns bounded asset ledger records visible to the authenticated tenant actor.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"category":{"type":"string","maxLength":80},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","assetCode","name","category","status","version","canEdit","canDelete"],"properties":{"id":{"type":"integer","minimum":1},"assetCode":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":160},"category":{"type":"string","maxLength":80},"specification":{"type":"string","maxLength":500},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"departmentName":{"type":"string","maxLength":120},"ownerName":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","maxLength":10},"originalValue":{"type":"number","minimum":0},"remark":{"type":"string","maxLength":1000},"version":{"type":"integer","minimum":0},"canEdit":{"type":"boolean"},"canDelete":{"type":"boolean"},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:c0c6bae0a712cbbfbef1cc741fb50af140a3cde98603e8bed8a90294ad5810b1',
 'L0','["assets:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
