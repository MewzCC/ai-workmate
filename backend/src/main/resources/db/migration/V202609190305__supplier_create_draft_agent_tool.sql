INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:supplier.createDraft','Agent 创建供应商草稿','AI 能力',
       '允许 Agent 在显式确认后创建一条不含敏感联系资料的租户内供应商草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:supplier.createDraft'
FROM rbac_role_permission WHERE permission_code='supplier:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'supplier.createDraft','Create a supplier draft',
 'Creates one tenant-scoped supplier draft without contact, credit or risk data.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name","category","supplierLevel"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"shortName":{"type":"string","maxLength":80},"category":{"type":"string","enum":["MATERIAL","SERVICE","LOGISTICS","CONSULTING","OTHER"]},"supplierLevel":{"type":"string","enum":["STRATEGIC","PREFERRED","STANDARD","RESTRICTED"]},"paymentTerms":{"type":"string","maxLength":120}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["supplierId","code","status","version","updatedAt"],"properties":{"supplierId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:9d859cfb46661e9c8994e04a29966aa378f2ef717eb5f62f6e8b5d434add1fdb',
 'L1','["supplier:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
