INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:tool:asset.transfer','Agent 调拨单个资产','允许 Agent 在显式确认后调拨一项版本匹配的资产'),
 ('agent:tool:asset.repair.complete','Agent 完成单个资产维修','允许 Agent 在显式确认后完成一项资产维修'),
 ('agent:tool:asset.inventory','Agent 盘点单个资产','允许 Agent 在显式确认后登记一项资产盘点结果'),
 ('agent:tool:asset.scrap','Agent 报废单个资产','允许 Agent 在二次确认后将一项符合状态的资产标记为报废')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,permission.code
FROM rbac_role_permission source CROSS JOIN (VALUES ('agent:tool:asset.transfer'),
 ('agent:tool:asset.repair.complete'),('agent:tool:asset.inventory'),('agent:tool:asset.scrap')) AS permission(code)
WHERE source.permission_code='asset:write' ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'asset.transfer','Transfer one asset','Transfers one version-bound asset to one valid tenant department and optional custodian.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","targetDepartmentId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"targetDepartmentId":{"type":"integer","minimum":1},"targetOwnerUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version","action"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":1},"action":{"type":"string","enum":["TRANSFER","REPAIR_COMPLETE","INVENTORY","SCRAP"]}}}'::jsonb,
 'sha256:eb9cb431ae38dbd28812ecc75f18eb28d037fe559e7c7cbd5e2453ee1af081c2','L1','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'asset.repair.complete','Complete one asset repair','Completes one repairing asset and returns it to the idle pool.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version","reason"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version","action"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":1},"action":{"type":"string","enum":["TRANSFER","REPAIR_COMPLETE","INVENTORY","SCRAP"]}}}'::jsonb,
 'sha256:a989b519e0620bd6e3e7bfcdf8f49c3d380595c777d7c79c74326cf0a7a22727','L1','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'asset.inventory','Record one asset inventory result','Records one bounded physical inventory result without bulk reconciliation.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version","inventoryResult"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"inventoryResult":{"type":"string","enum":["MATCH","MISSING","DAMAGED","LOCATION_MISMATCH","CUSTODIAN_MISMATCH"]},"actualStatus":{"type":"string","enum":["IN_USE","IDLE","REPAIRING","SCRAPPED"]},"actualDepartmentId":{"type":"integer","minimum":1},"actualOwnerUserId":{"type":"integer","minimum":1},"reason":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version","action"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":1},"action":{"type":"string","enum":["TRANSFER","REPAIR_COMPLETE","INVENTORY","SCRAP"]}}}'::jsonb,
 'sha256:33bdadb187daf248f3430859902303392c3f6820a9d70ea1b52ad4362e3e798a','L1','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,8192,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'asset.scrap','Scrap one asset','Irreversibly marks one eligible asset as scrapped without deleting its history.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version","reason"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","status","version","action"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":1},"action":{"type":"string","enum":["TRANSFER","REPAIR_COMPLETE","INVENTORY","SCRAP"]}}}'::jsonb,
 'sha256:a989b519e0620bd6e3e7bfcdf8f49c3d380595c777d7c79c74326cf0a7a22727','L2','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','SECONDARY',1,8192,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
