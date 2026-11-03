-- Minimal business-owned person selectors. No write switch or business permission is enabled here.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT v.code,v.name,'AI 能力',v.description,t.id
FROM tenant t CROSS JOIN (VALUES
 ('agent:tool:hr.change.reviewer.query','Agent 查询人事变动审批人','只读同租户有效且具有实时人事管理权限的候选人'),
 ('agent:tool:visitor.host.query','Agent 查询访客接待人','只读同租户有效接待人的编号与姓名')
) AS v(code,name,description)
WHERE t.code='DEFAULT' ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT rp.tenant_id,rp.role_code,v.tool_permission
FROM rbac_role_permission rp JOIN (VALUES
 ('hr:manage','agent:tool:hr.change.reviewer.query'),
 ('visitor:create','agent:tool:visitor.host.query')
) AS v(business_permission,tool_permission) ON v.business_permission=rp.permission_code
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
SELECT NULL,v.code,v.name,v.description,'1.0.0',
 '{"type":"object","additionalProperties":false,"required":[],"properties":{"keyword":{"type":"string","maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["id","name"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,
 'sha256:3559945d8967b3f310ea920bdfafc8fc9836f306784de53e38d6ed3decea4231','L0',v.permissions::jsonb,
 'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',20,16384,15000,'HASHED_ARGS_RESULT',TRUE
FROM (VALUES
 ('hr.change.reviewer.query','Query employee change reviewers','Lists active tenant reviewers with live hr:manage permission, excluding the applicant.','["hr:manage"]'),
 ('visitor.host.query','Query visitor hosts','Lists active host candidates in the authenticated tenant using visitor creation permission.','["visitor:create"]')
) AS v(code,name,description,permissions)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
