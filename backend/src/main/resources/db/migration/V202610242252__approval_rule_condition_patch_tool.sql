INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.rule.patchDraftCondition','Agent 局部修改规则条件值','AI 能力',
       '明确确认后仅修改一个禁用规则的一个条件值，保留其他条件、动作与元数据；默认关闭',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.rule.patchDraftCondition'
FROM rbac_role_permission WHERE permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.rule.patchDraftCondition','Patch one approval rule draft condition',
 'Changes only one existing condition value in one disabled tenant rule using an expected version and one-based condition number.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["ruleId","version","conditionNumber","value"],"properties":{"ruleId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1,"maximum":2147483646},"conditionNumber":{"type":"integer","minimum":1,"maximum":10},"value":{"type":"string","minLength":1,"maxLength":120}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["ruleId","version","status","conditionNumber","field","operator","value"],"properties":{"ruleId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":2,"maximum":2147483647},"status":{"type":"string","const":"DISABLED"},"conditionNumber":{"type":"integer","minimum":1,"maximum":10},"field":{"type":"string","enum":["amount","durationDays","department","employeeLevel","leaveType"]},"operator":{"type":"string","enum":["eq","ne","gt","gte","lt","lte","in"]},"value":{"type":"string","minLength":1,"maxLength":120}}}'::jsonb,
 'sha256:588a77eb3294d8955af4c31a203a16303a7c37f8ab54165375ef9403bf994b24',
 'L1','["approval:manage"]'::jsonb,'ALL','FIXED_RESOURCE','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
