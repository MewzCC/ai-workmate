INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:meeting.query','Agent 查询会议室','AI 能力',
       '允许 Agent 查询租户会议室及当前用户预约安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:meeting.query'
FROM rbac_role_permission WHERE permission_code='meeting:read:self'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'meeting.query','Query meeting rooms and my bookings',
 'Returns bounded tenant meeting rooms and bookings owned by the authenticated actor.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"roomStatus":{"type":"string","enum":["OPEN","CLOSED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"bookingStatus":{"type":"string","enum":["BOOKED","CANCELLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["rooms","bookings","bookingTotal","page","size"],"properties":{"rooms":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","capacity","status","canEdit","canDelete"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":160},"location":{"type":"string","maxLength":255},"capacity":{"type":"integer","minimum":0},"facilities":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":1000},"canEdit":{"type":"boolean"},"canDelete":{"type":"boolean"}}}},"bookings":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","roomId","title","startAt","endAt","attendeeCount","status","version","canCancel"],"properties":{"id":{"type":"integer","minimum":1},"roomId":{"type":"integer","minimum":1},"roomCode":{"type":"string","maxLength":80},"roomName":{"type":"string","maxLength":160},"roomLocation":{"type":"string","maxLength":255},"organizerName":{"type":"string","maxLength":120},"title":{"type":"string","maxLength":200},"agenda":{"type":"string","maxLength":2000},"startAt":{"type":"string","maxLength":32},"endAt":{"type":"string","maxLength":32},"attendeeCount":{"type":"integer","minimum":1},"status":{"type":"string","enum":["BOOKED","CANCELLED"]},"version":{"type":"integer","minimum":0},"cancelledByName":{"type":"string","maxLength":120},"cancelledAt":{"type":"string","maxLength":32},"cancelReason":{"type":"string","maxLength":1000},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32},"canCancel":{"type":"boolean"}}}},"bookingTotal":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:48a529200e1b903fe1623c118d9fc78b4504bcc8dbda8202cc056f83420db25b',
 'L0','["meeting:read:self"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
