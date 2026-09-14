INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:visitor.query','Agent 查询访客预约','AI 能力',
       '允许 Agent 通过受控只读工具查询当前用户可见的访客预约安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:seal.query','Agent 查询用印申请','AI 能力',
       '允许 Agent 通过受控只读工具查询当前用户可见的用印申请安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:visitor.query'
FROM rbac_role_permission WHERE permission_code='visitor:read:self'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:seal.query'
FROM rbac_role_permission WHERE permission_code='seal:read:self'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.query','Query my visitor bookings',
 'Returns a bounded owned, assigned or explicitly visible visitor booking summary.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"bookingId":{"type":"integer","minimum":1},"queue":{"type":"string","enum":["MINE","PENDING"]},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["bookingId"],"not":{"anyOf":[{"required":["queue"]},{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["bookingId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","hostName","visitorName","purpose","expectedVisitAt","expectedLeaveAt","partySize","status","version","canWithdraw","canDecide","canCheckIn","canMarkVisited","canLeave","canMarkNoShow"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"hostName":{"type":"string","maxLength":120},"visitorName":{"type":"string","maxLength":120},"visitorCompany":{"type":"string","maxLength":200},"purpose":{"type":"string","maxLength":1000},"expectedVisitAt":{"type":"string","maxLength":32},"expectedLeaveAt":{"type":"string","maxLength":32},"partySize":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"version":{"type":"integer","minimum":0},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"registeredByName":{"type":"string","maxLength":120},"checkedInAt":{"type":"string","maxLength":32},"visitedAt":{"type":"string","maxLength":32},"leftAt":{"type":"string","maxLength":32},"noShowAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canCheckIn":{"type":"boolean"},"canMarkVisited":{"type":"boolean"},"canLeave":{"type":"boolean"},"canMarkNoShow":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:aa6f923d24734c45f3022026885641deb9b74abbe6e9061394234e5cc739a3aa',
 'L0','["visitor:read:self"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'seal.query','Query my seal usages',
 'Returns a bounded owned, assigned or explicitly visible seal usage summary.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"usageId":{"type":"integer","minimum":1},"queue":{"type":"string","enum":["MINE","PENDING"]},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["usageId"],"not":{"anyOf":[{"required":["queue"]},{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["usageId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","sealType","documentTitle","usageReason","copies","status","version","canWithdraw","canDecide","canRegisterUse","canReturn","canArchiveDocument"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"sealType":{"type":"string","maxLength":80},"documentTitle":{"type":"string","maxLength":255},"usageReason":{"type":"string","maxLength":1000},"copies":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"actualCopies":{"type":"integer","minimum":1},"handlerName":{"type":"string","maxLength":120},"usedAt":{"type":"string","maxLength":32},"returnedAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canRegisterUse":{"type":"boolean"},"canReturn":{"type":"boolean"},"canArchiveDocument":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175',
 'L0','["seal:read:self"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
