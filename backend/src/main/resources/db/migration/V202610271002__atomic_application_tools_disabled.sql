-- New atomic write capabilities remain disabled until the independent release gate is satisfied.

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.application.apply','Agent 原子提交通用申请','AI 能力','Atomically creates and submits one self-owned generic form application after explicit confirmation.',id
FROM tenant WHERE code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT creator.tenant_id,creator.role_code,'agent:tool:approval.application.apply'
FROM rbac_role_permission creator
JOIN rbac_role_permission submitter ON submitter.tenant_id=creator.tenant_id AND submitter.role_code=creator.role_code
WHERE creator.permission_code='approval:create' AND submitter.permission_code='approval:submit'
ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.application.apply','Apply for a generic approval','Atomically creates and submits one self-owned generic form application after explicit confirmation.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["formKey","fields"],"properties":{"formKey":{"type":"string","minLength":1,"maxLength":64},"processKey":{"type":"string","minLength":1,"maxLength":64},"fields":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":64},"value":{"type":"string","maxLength":500},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}}},"oneOf":[{"required":["value"],"not":{"required":["values"]}},{"required":["values"],"not":{"required":["value"]}}]}}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,'sha256:c614bce4890b0f2b8ae04b4b75000c73f4184fe0be2133a9fc84b4f341e5db1e',
 'L1','["approval:create","approval:submit"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.apply','Agent 原子提交费用申请','AI 能力','Atomically creates and submits one self-owned expense application after explicit confirmation.',id
FROM tenant WHERE code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT creator.tenant_id,creator.role_code,'agent:tool:expense.apply'
FROM rbac_role_permission creator
JOIN rbac_role_permission submitter ON submitter.tenant_id=creator.tenant_id AND submitter.role_code=creator.role_code
WHERE creator.permission_code='approval:create' AND submitter.permission_code='approval:submit'
ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.apply','Apply for an expense reimbursement','Atomically creates and submits one self-owned expense application after explicit confirmation.','1.0.0',
 '{"type":"object","additionalProperties":false,"minProperties":1,"properties":{"amount":{"type":"number","minimum":0.01,"maximum":999999999.99,"multipleOf":0.01},"category":{"type":"string","enum":["TRAVEL","MEAL","TRANSPORT","OFFICE","OTHER"]},"expenseDate":{"type":"string","format":"date"},"invoiceNumber":{"type":"string","minLength":1,"maxLength":100},"reason":{"type":"string","minLength":1,"maxLength":1000}},"required":["amount","category","expenseDate","reason"]}'::jsonb,'{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb,'sha256:ce17ae535efa0de9beed88a10c326cafc7831f98176523b4bf52d6a0cfd7d371',
 'L1','["approval:create","approval:submit"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',FALSE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

