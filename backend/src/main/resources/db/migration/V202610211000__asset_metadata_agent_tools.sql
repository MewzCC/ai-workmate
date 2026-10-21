INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.create','Agent 创建资产资料','AI 能力','确认后单条维护资产资料，默认关闭，不自动重试或改变生命周期',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.create' FROM rbac_role_permission
WHERE permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.create','Create one asset',
 'Single tenant asset metadata write after confirmation; no retry or lifecycle change.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetCode","name","category"],"properties":{"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"category":{"type":"string","minLength":1,"maxLength":40},"specification":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","format":"date","minLength":10,"maxLength":10},"originalValue":{"type":"number","minimum":0,"maximum":999999999999.99,"multipleOf":0.01},"remark":{"type":"string","maxLength":500},"departmentId":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","assetCode","name","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:de7519ee9fd9f0c13b082e4754f2cd96435b0fdada088d70e2fa4f5933f14e5e','L1','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:asset.update','Agent 修改资产资料','AI 能力','确认后单条维护资产资料，默认关闭，不自动重试或改变生命周期',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:asset.update' FROM rbac_role_permission
WHERE permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'asset.update','Update one asset',
 'Single tenant asset metadata write after confirmation; no retry or lifecycle change.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["assetId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"category":{"type":"string","minLength":1,"maxLength":40},"specification":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","format":"date","minLength":10,"maxLength":10},"originalValue":{"type":"number","minimum":0,"maximum":999999999999.99,"multipleOf":0.01},"remark":{"type":"string","maxLength":500},"clearPurchaseDate":{"type":"boolean","const":true}},"minProperties":3,"not":{"required":["purchaseDate","clearPurchaseDate"]}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["assetId","assetCode","name","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:84879740ee0225517991384a90bf2794fcdcf6675de9b8b551d4c3018c6717cc','L1','["asset:write"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
