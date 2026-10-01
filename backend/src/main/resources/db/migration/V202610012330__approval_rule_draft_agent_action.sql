INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.rule.createDraft','Agent 创建审批规则草稿','AI 能力',
       '允许 Agent 经明确确认后用受控条件和动作创建一个未启用的审批规则草稿',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:approval.rule.createDraft'
FROM rbac_role_permission grant_row WHERE grant_row.permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'approval.rule.createDraft','Create an approval rule draft',
 'Creates one disabled tenant approval rule from bounded semantic conditions and action.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["ruleKey","ruleName","ruleType","priority","logic","conditions","action"],"properties":{"ruleKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"ruleName":{"type":"string","minLength":1,"maxLength":120},"ruleType":{"type":"string","enum":["AMOUNT_THRESHOLD","LEAVE_TYPE","EMPLOYEE_LEVEL","LIMIT_OVERRIDE"]},"priority":{"type":"integer","minimum":0,"maximum":10000},"description":{"type":"string","maxLength":500},"logic":{"type":"string","enum":["AND","OR"]},"conditions":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["field","operator","value"],"properties":{"field":{"type":"string","enum":["amount","durationDays","department","employeeLevel","leaveType"]},"operator":{"type":"string","enum":["eq","ne","gt","gte","lt","lte","in"]},"value":{"type":"string","minLength":1,"maxLength":120}}}},"action":{"type":"object","additionalProperties":false,"required":["appendNode","enabled","mode"],"properties":{"appendNode":{"type":"string","enum":["DEPARTMENT_HEAD","FINANCE_REVIEW","DIRECT_MANAGER"]},"enabled":{"type":"boolean"},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]}}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["ruleId","ruleKey","status","version","updatedAt"],"properties":{"ruleId":{"type":"integer","minimum":1},"ruleKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:847518614624e4e86c659cf1656265ae7e76786a006538d58f2277276c5acc11','L1',
 '["approval:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,16384,15000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
