INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.rule.definition.query','Agent 读取审批规则定义','AI 能力',
       '只读当前租户一份规则的受控条件、动作和版本，不返回原始结构或创建人资料',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.rule.definition.query'
FROM rbac_role_permission WHERE permission_code='approval:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.rule.definition.query','Read an approval rule definition',
 'Returns one authorized tenant rule as bounded conditions and actions with its actual version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["ruleId"],"properties":{"ruleId":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["ruleKey","ruleName","ruleType","priority","logic","conditions","action","ruleId","status","version"],"properties":{"ruleKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"ruleName":{"type":"string","minLength":1,"maxLength":120},"ruleType":{"type":"string","enum":["AMOUNT_THRESHOLD","LEAVE_TYPE","EMPLOYEE_LEVEL","LIMIT_OVERRIDE"]},"priority":{"type":"integer","minimum":0,"maximum":2147483647},"logic":{"type":"string","enum":["AND","OR"]},"conditions":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["field","operator","value"],"properties":{"field":{"type":"string","enum":["amount","durationDays","department","employeeLevel","leaveType"]},"operator":{"type":"string","enum":["eq","ne","gt","gte","lt","lte","in"]},"value":{"type":"string","minLength":1,"maxLength":120}}}},"action":{"type":"object","additionalProperties":false,"required":["appendNode","enabled","mode"],"properties":{"appendNode":{"type":"string","enum":["DEPARTMENT_HEAD","FINANCE_REVIEW","DIRECT_MANAGER"]},"enabled":{"type":"boolean"},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]}}},"ruleId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:a595eafd0035099b68fcf7f97867ea848d13cb214cd2bceda203936fd2031b23',
 'L0','["approval:read"]'::jsonb,'ALL','FIXED_RESOURCE','READ_ONLY_SAFE','NONE','NONE',
 10,16384,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
