INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.updateDraft','Agent 更新费用草稿','AI 能力',
       '允许 Agent 在显式确认后更新当前用户费用草稿的指定字段',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:expense.updateDraft'
FROM rbac_role_permission WHERE permission_code='approval:create'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.updateDraft','Update my expense draft',
 'Updates selected fields on one self-owned expense draft without submitting it.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"minProperties":3,"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"amount":{"type":"number","minimum":0.01,"maximum":999999999.99,"multipleOf":0.01},"category":{"type":"string","enum":["TRAVEL","MEAL","TRANSPORT","OFFICE","OTHER"]},"expenseDate":{"type":"string","format":"date"},"invoiceNumber":{"type":"string","minLength":1,"maxLength":100},"reason":{"type":"string","minLength":1,"maxLength":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:e08fd6a543d60ed628123a93a209bd1c2d330d940530531e095dd9bc0ca99fcc',
 'L1','["approval:create"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
