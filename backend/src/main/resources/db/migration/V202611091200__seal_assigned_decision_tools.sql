-- Assigned seal decisions stay disabled until the independent release gate.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT v.code,v.name,'AI 能力',v.description,t.id FROM tenant t CROSS JOIN (VALUES
 ('agent:tool:seal.approve','Agent 通过单个用印审批','二次确认后使用任务版本通过本人受理的一个用印任务'),
 ('agent:tool:seal.reject','Agent 驳回单个用印审批','明确理由并二次确认后使用任务版本驳回本人受理的一个用印任务')
) AS v(code,name,description) WHERE t.code='DEFAULT' ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT r.tenant_id,r.role_code,v.code FROM rbac_role_permission r
CROSS JOIN (VALUES ('agent:tool:seal.approve'),('agent:tool:seal.reject')) AS v(code)
WHERE r.permission_code='approval:act' ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'seal.approve','Approve one assigned seal task',
 'Decides one assigned seal approval task using its task version, independently of the usage version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","maxLength":500}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["resourceId","resourceType","resourceStatus","resourceVersion","taskId","status","version","action"],"properties":{"resourceId":{"type":"integer","minimum":1},"resourceType":{"type":"string","enum":["SEAL_USAGE"]},"resourceStatus":{"type":"string","enum":["APPROVED"]},"resourceVersion":{"type":"integer","minimum":1,"maximum":2147483647},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"action":{"type":"string","enum":["APPROVE"]}}}'::jsonb,
 'sha256:002f1702879a327dd4e1b93492dc7ed1207fd94357947227f9b5d0095b509e78','L2','["approval:act"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE',
 'SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'seal.reject','Reject one assigned seal task',
 'Decides one assigned seal approval task using its task version, independently of the usage version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["resourceId","resourceType","resourceStatus","resourceVersion","taskId","status","version","action"],"properties":{"resourceId":{"type":"integer","minimum":1},"resourceType":{"type":"string","enum":["SEAL_USAGE"]},"resourceStatus":{"type":"string","enum":["REJECTED"]},"resourceVersion":{"type":"integer","minimum":1,"maximum":2147483647},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["REJECTED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"action":{"type":"string","enum":["REJECT"]}}}'::jsonb,
 'sha256:799f7a4f8da087bb59453dbc293d1ec3ddd60e97644395a57251735c361861bb','L2','["approval:act"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE',
 'SECONDARY',1,8192,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

-- Preserve enablement and custom unknown contracts; do not rewrite frozen task snapshots.
UPDATE agent_tool SET output_schema='{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","sealType","documentTitle","usageReason","copies","status","version","canWithdraw","canDecide","canRegisterUse","canReturn","canArchiveDocument"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"sealType":{"type":"string","maxLength":80},"documentTitle":{"type":"string","maxLength":255},"usageReason":{"type":"string","maxLength":1000},"copies":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"taskId":{"type":"integer","minimum":1},"taskVersion":{"type":"integer","minimum":0,"maximum":2147483646},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"actualCopies":{"type":"integer","minimum":1},"handlerName":{"type":"string","maxLength":120},"usedAt":{"type":"string","maxLength":32},"returnedAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canRegisterUse":{"type":"boolean"},"canReturn":{"type":"boolean"},"canArchiveDocument":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 schema_hash='sha256:b44aae05e49a951b29dbcc25335a9852da4ba06565b8af39f011a6f26fae6584',updated_at=CURRENT_TIMESTAMP
WHERE code='seal.query' AND handler_version='1.0.0'
 AND schema_hash='sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175';
