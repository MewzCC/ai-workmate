INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.room.query','Agent 查询会议室资料','AI 能力','分页读取本租户会议室资料及当前版本，不返回预约记录或身份',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.room.query' FROM rbac_role_permission
WHERE permission_code IN ('assets:read','meeting:read:self')
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.room.query','Query meeting room metadata',
 'Pages tenant room metadata with current version.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["roomId","code","name","capacity","status","version"],"properties":{"roomId":{"type":"integer","minimum":1},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500},"version":{"type":"integer","minimum":0}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:45bc82f82e3dab815be1eb58da17701d69d5ea5266af20f829099968b057d114','L0',
 '["assets:read","meeting:read:self"]'::jsonb,'ANY','TENANT_SCOPED',
 'READ_ONLY_SAFE','NONE','NONE',
 50,262144,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.room.create','Agent 创建会议室资料','AI 能力','确认后创建单个会议室，默认关闭，不自动重试或删除',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.room.create' FROM rbac_role_permission
WHERE permission_code = 'meeting:write'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.room.create','Create one meeting room',
 'Changes exactly one tenant room after confirmation without automatic retry.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["code","name","capacity","status"],"properties":{"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0,"maximum":10000},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["roomId","code","name","status","version"],"properties":{"roomId":{"type":"integer","minimum":1},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["OPEN","CLOSED"]},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:eb0a73f37a5acd02ff01ba4465f9c768a54d69d543dbbb63c8f0fb52fcb65539','L1',
 '["meeting:write"]'::jsonb,'ALL','TENANT_SCOPED',
 'NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.room.update','Agent 修改会议室资料','AI 能力','确认后按版本修改单个会议室指定字段，保留未提供字段，默认关闭',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.room.update' FROM rbac_role_permission
WHERE permission_code = 'meeting:write'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.room.update','Update one meeting room',
 'Changes exactly one tenant room after confirmation without automatic retry.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["roomId","version"],"properties":{"roomId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0,"maximum":10000},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500}},"minProperties":3}'::jsonb,'{"type":"object","additionalProperties":false,"required":["roomId","code","name","status","version"],"properties":{"roomId":{"type":"integer","minimum":1},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["OPEN","CLOSED"]},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:b4778abcb5352b23af844b4f466e619414ea72d9a818c5371830eb3c4fcde84b','L1',
 '["meeting:write"]'::jsonb,'ALL','TENANT_SCOPED',
 'NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
