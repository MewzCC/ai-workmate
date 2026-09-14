INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'expense:read:self','读取本人费用报销','财务合同','读取当前用户自己的费用报销摘要',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'expense:read:self' FROM rbac_role_permission WHERE permission_code='route:expense'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:expense.query','Agent 查询费用报销','AI 能力','允许 Agent 通过受控只读工具查询财务页面安全摘要',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:expense.query' FROM rbac_role_permission WHERE permission_code='expense:read:self'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:budget.query','Agent 查询预算','AI 能力','允许 Agent 通过受控只读工具查询财务页面安全摘要',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:budget.query' FROM rbac_role_permission WHERE permission_code='budget:manage'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:contract.query','Agent 查询合同','AI 能力','允许 Agent 通过受控只读工具查询财务页面安全摘要',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:contract.query' FROM rbac_role_permission WHERE permission_code='contract:manage'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:supplier.query','Agent 查询供应商','AI 能力','允许 Agent 通过受控只读工具查询财务页面安全摘要',id FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:supplier.query' FROM rbac_role_permission WHERE permission_code='supplier:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'expense.query','Query my expenses','Returns bounded expense summaries owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["applicationId"]},{"not":{"required":["applicationId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","status","version"],"properties":{"id":{"type":"integer","minimum":1},"title":{"type":"string","maxLength":255},"amount":{"type":"number"},"category":{"type":"string","maxLength":40},"expenseDate":{"type":"string","format":"date"},"invoiceNumber":{"type":"string","maxLength":100},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","maxLength":40},"version":{"type":"integer"},"approverName":{"type":"string","maxLength":120},"dueAt":{"type":"string","maxLength":32},"submittedAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"canRemind":{"type":"boolean"},"canWithdraw":{"type":"boolean"},"canEditDraft":{"type":"boolean"},"canCancel":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:054bddbc37bd581b6feae1ad51fc702f1b5c48a4b93575b174eefbbe185f74c8','L0','["expense:read:self"]'::jsonb,'ALL','SELF','READ_ONLY_SAFE','NONE','NONE',
 50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'budget.query','Query budgets','Returns bounded budget summaries after live finance authorization.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"budgetId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","maxLength":40},"fiscalYear":{"type":"integer","minimum":2000,"maximum":2100},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["budgetId"]},{"not":{"required":["budgetId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status","version"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":200},"fiscalYear":{"type":"integer"},"ownerLabel":{"type":"string","maxLength":120},"totalAmount":{"type":"number"},"occupiedAmount":{"type":"number"},"spentAmount":{"type":"number"},"availableAmount":{"type":"number"},"currency":{"type":"string","maxLength":8},"utilizationPercent":{"type":"integer"},"alertLevel":{"type":"string","maxLength":40},"status":{"type":"string","maxLength":40},"summary":{"type":"string","maxLength":1000},"version":{"type":"integer"},"updatedAt":{"type":"string","maxLength":32},"canManage":{"type":"boolean"},"allowedTransitions":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":40}}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:7d700b1b7b6c3556ed14833b9d7d1a4bb81e461a305afb108f4b75e651dd2760','L0','["budget:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',
 50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'contract.query','Query contracts','Returns bounded contract summaries without internal identity fields.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"contractId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","maxLength":40},"contractType":{"type":"string","maxLength":40},"expiryState":{"type":"string","maxLength":40},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["contractId"]},{"not":{"required":["contractId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status","version"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":200},"contractType":{"type":"string","maxLength":40},"counterpartyName":{"type":"string","maxLength":200},"supplierLabel":{"type":"string","maxLength":200},"ownerLabel":{"type":"string","maxLength":120},"amount":{"type":"number"},"paidAmount":{"type":"number"},"currency":{"type":"string","maxLength":8},"signedDate":{"type":"string","format":"date"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"status":{"type":"string","maxLength":40},"fulfillmentStatus":{"type":"string","maxLength":40},"expiryState":{"type":"string","maxLength":40},"daysUntilExpiry":{"type":"integer"},"summary":{"type":"string","maxLength":1000},"version":{"type":"integer"},"updatedAt":{"type":"string","maxLength":32},"canManage":{"type":"boolean"},"canRecordPayment":{"type":"boolean"},"canRemind":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:51c12cd5d1f1b3297dfce9fe7d11e0f7d603766dfbd3aeb1fb0584f775acb02b','L0','["contract:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',
 50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'supplier.query','Query suppliers','Returns bounded supplier summaries without contact or credit identifiers.','1.0.0',
 '{"type":"object","additionalProperties":false,"properties":{"supplierId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","maxLength":40},"category":{"type":"string","maxLength":80},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["supplierId"]},{"not":{"required":["supplierId"]}}]}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status","version"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":200},"shortName":{"type":"string","maxLength":120},"category":{"type":"string","maxLength":80},"supplierLevel":{"type":"string","maxLength":40},"status":{"type":"string","maxLength":40},"paymentTerms":{"type":"string","maxLength":500},"version":{"type":"integer"},"updatedAt":{"type":"string","maxLength":32},"canManage":{"type":"boolean"},"allowedTransitions":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":40}}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:7adf543de65a87fa28bbd4713d1c42f44e3522a90bacd00c12e178917d77ebe0','L0','["supplier:manage"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',
 50,196608,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
