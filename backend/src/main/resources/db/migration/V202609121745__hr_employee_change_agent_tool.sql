INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:hr.change.query','Agent 查询入转调离','AI 能力',
       '允许 Agent 按实时租户权限只读查询员工变动安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:hr.change.query'
FROM rbac_role_permission WHERE permission_code='hr:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'hr.change.query','Query employee changes',
 'Returns bounded employee changes visible to the authenticated tenant actor.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PENDING","APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"keyword":{"type":"string","maxLength":200},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","employeeName","applicantName","reviewApproverName","changeType","effectiveDate","reason","status","version","canApprove","canWithdraw"],"properties":{"id":{"type":"integer","minimum":1},"employeeName":{"type":"string","maxLength":120},"applicantName":{"type":"string","maxLength":120},"reviewApproverName":{"type":"string","maxLength":120},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"effectiveDate":{"type":"string","maxLength":10},"currentDepartmentName":{"type":"string","maxLength":120},"currentPositionName":{"type":"string","maxLength":120},"targetDepartmentName":{"type":"string","maxLength":120},"targetPositionName":{"type":"string","maxLength":120},"targetSupervisorName":{"type":"string","maxLength":120},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["PENDING","APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":0},"canApprove":{"type":"boolean"},"canWithdraw":{"type":"boolean"},"submittedAt":{"type":"string","maxLength":32},"decidedAt":{"type":"string","maxLength":32},"appliedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:e7776fa61d680c418f2e210d5e0d7dd9d93d3d7d1ab153d7a81c224b8e457c53',
 'L0','["hr:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
