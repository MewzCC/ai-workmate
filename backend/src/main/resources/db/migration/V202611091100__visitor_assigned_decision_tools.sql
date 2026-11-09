-- Fixed assigned visitor decisions. No release switch or existing tool enablement is changed.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT v.code,v.name,'AI 能力',v.description,t.id FROM tenant t CROSS JOIN (VALUES
 ('agent:tool:visitor.approve','Agent 通过单个访客审批','二次确认后使用任务版本通过本人受理的一个访客任务'),
 ('agent:tool:visitor.reject','Agent 驳回单个访客审批','明确理由并二次确认后使用任务版本驳回本人受理的一个访客任务')
) AS v(code,name,description) WHERE t.code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT r.tenant_id,r.role_code,v.code FROM rbac_role_permission r
CROSS JOIN (VALUES ('agent:tool:visitor.approve'),('agent:tool:visitor.reject')) AS v(code)
WHERE r.permission_code='approval:act' ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.approve','Approve one assigned visitor task',
 'Decides one assigned visitor approval task using its task version, independently of the booking version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","maxLength":500}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["resourceId","resourceType","resourceStatus","resourceVersion","taskId","status","version","action"],"properties":{"resourceId":{"type":"integer","minimum":1},"resourceType":{"type":"string","enum":["VISITOR_BOOKING"]},"resourceStatus":{"type":"string","enum":["APPROVED"]},"resourceVersion":{"type":"integer","minimum":1,"maximum":2147483647},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"action":{"type":"string","enum":["APPROVE"]}}}'::jsonb,
 'sha256:8b22e26d324740577f6bf91ba4b639b150e88dbb505b3f3f71c895729e1bdb19','L2','["approval:act"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE',
 'SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'visitor.reject','Reject one assigned visitor task',
 'Decides one assigned visitor approval task using its task version, independently of the booking version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["resourceId","resourceType","resourceStatus","resourceVersion","taskId","status","version","action"],"properties":{"resourceId":{"type":"integer","minimum":1},"resourceType":{"type":"string","enum":["VISITOR_BOOKING"]},"resourceStatus":{"type":"string","enum":["REJECTED"]},"resourceVersion":{"type":"integer","minimum":1,"maximum":2147483647},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["REJECTED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"action":{"type":"string","enum":["REJECT"]}}}'::jsonb,
 'sha256:f52f018c3ee70fb304651a915c223fc1da0f8de16ff68cc87502e00e3bc16deb','L2','["approval:act"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE',
 'SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

-- New task coordinates are only projected for an actionable assigned task.
-- Frozen tasks using the earlier read schema must be re-planned; do not rewrite their snapshots.
UPDATE agent_tool SET output_schema='{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","hostName","visitorName","purpose","expectedVisitAt","partySize","status","version","canWithdraw","canDecide","canCheckIn","canMarkVisited","canLeave","canMarkNoShow"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"hostName":{"type":"string","maxLength":120},"visitorName":{"type":"string","maxLength":120},"visitorCompany":{"type":"string","maxLength":200},"purpose":{"type":"string","maxLength":1000},"expectedVisitAt":{"type":"string","maxLength":32},"expectedLeaveAt":{"type":"string","maxLength":32},"partySize":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"version":{"type":"integer","minimum":0},"taskId":{"type":"integer","minimum":1},"taskVersion":{"type":"integer","minimum":0,"maximum":2147483646},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"registeredByName":{"type":"string","maxLength":120},"checkedInAt":{"type":"string","maxLength":32},"visitedAt":{"type":"string","maxLength":32},"leftAt":{"type":"string","maxLength":32},"noShowAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canCheckIn":{"type":"boolean"},"canMarkVisited":{"type":"boolean"},"canLeave":{"type":"boolean"},"canMarkNoShow":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 schema_hash='sha256:2a4a406ac690c685b4df2397e1fe65090b12c043471f2d03bab0f07f24bb2844',updated_at=CURRENT_TIMESTAMP
WHERE code='visitor.query' AND handler_version='1.0.0'
 AND schema_hash='sha256:0ee54fb81f4e29f20108018ac3449c19f747fa481249a87b67d06d8e8479891e';
