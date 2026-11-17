-- Metadata-only capabilities remain disabled pending the independent release gate.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:'||d.code,d.name,'AI 能力',d.description,t.id
FROM (VALUES
 ('hr.employee.documents.query','查询员工档案附件信息','仅在员工可见范围内读取受控附件元数据，不读取内容或存储坐标'),
 ('seal.documents.query','查询用印留档附件信息','仅在关联用印资源权限内读取附件元数据，不读取内容或存储坐标')
) AS d(code,name,description) CROSS JOIN tenant t WHERE t.code='DEFAULT'
ON CONFLICT(code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT r.tenant_id,r.role_code,'agent:tool:'||d.code
FROM rbac_role_permission r JOIN (VALUES
 ('hr.employee.documents.query','hr:read'),('seal.documents.query','seal:register')
) AS d(code,permission) ON r.permission_code=d.permission
ON CONFLICT DO NOTHING;

WITH definitions(code,name,description,input_schema,schema_hash,permission) AS (VALUES
 ('hr.employee.documents.query','Query visible employee document metadata',
  'Reads bounded document metadata for one employee in the current actor''s live data scope.',
  '{"type":"object","additionalProperties":false,"required":["employeeId"],"properties":{"employeeId":{"type":"integer","minimum":1},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
  'sha256:4219cab2607cabe01052d7d7a52fdb1689c33b7e0e5e10c60b29996d3dc162d5','hr:read'),
 ('seal.documents.query','Query related seal archive metadata',
  'Reads bounded archive metadata for one related seal usage after live permission and resource checks.',
  '{"type":"object","additionalProperties":false,"required":["usageId"],"properties":{"usageId":{"type":"integer","minimum":1},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
  'sha256:9ea5e25bb0c28519c4e8f04665dbc07a1ef5538e936fa914771940c812a3a1bd','seal:register')
), output_contract(schema) AS (VALUES (
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["documentId","documentType","displayName","mimeType","fileSize","createdAt"],"properties":{"documentId":{"type":"string","pattern":"^[1-9][0-9]{0,18}$","maxLength":19},"documentType":{"type":"string","enum":["CONTRACT","PROFILE","ARCHIVE"]},"displayName":{"type":"string","minLength":1,"maxLength":255},"mimeType":{"type":"string","minLength":1,"maxLength":120},"fileSize":{"type":"integer","minimum":1},"createdAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb))
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
SELECT NULL,d.code,d.name,d.description,'1.0.0',d.input_schema,o.schema,d.schema_hash,
 'L0',jsonb_build_array(d.permission),'ALL','FIXED_RESOURCE','READ_ONLY_SAFE','NONE','NONE',
 20,65536,15000,'HASHED_ARGS_RESULT',FALSE
FROM definitions d CROSS JOIN output_contract o
ON CONFLICT(code) WHERE tenant_id IS NULL DO NOTHING;
