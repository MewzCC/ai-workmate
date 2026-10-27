INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.process.createDraft','Agent 创建审批流程草稿','AI 能力',
       '允许 Agent 经明确确认后创建一个未发布的审批流程定义草稿',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,'agent:tool:approval.process.createDraft'
FROM rbac_role_permission grant_row WHERE grant_row.permission_code='approval:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'approval.process.createDraft','Create an approval process draft',
 'Creates one disabled tenant approval process from bounded semantic nodes.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["processKey","processName","nodes"],"properties":{"processKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"processName":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"formId":{"type":"integer","minimum":1},"nodes":{"type":"array","minItems":3,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["nodeType","nodeName"],"properties":{"nodeType":{"type":"string","enum":["START","APPROVAL","CONDITION","CC","DELAY","END"]},"nodeName":{"type":"string","minLength":1,"maxLength":80},"approveType":{"type":"string","enum":["DIRECT_MANAGER","ROLE","DEPARTMENT","USER","SELF","MULTI_LEVEL"]},"targetKey":{"type":"string","maxLength":80},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]},"timeoutEnabled":{"type":"boolean"},"timeoutHours":{"type":"integer","minimum":1,"maximum":720},"timeoutAction":{"type":"string","enum":["REMIND","TRANSFER","AUTO_APPROVE"]}}}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["processId","processKey","status","version","updatedAt"],"properties":{"processId":{"type":"integer","minimum":1},"processKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:6875532a42b510541241e91f7f4b71526d5f613d16a40f724728fc991c3ea23d','L1',
 '["approval:manage"]'::jsonb,'ALL','TENANT_SCOPED','NEVER','SINGLE_WRITE','EXPLICIT',1,16384,15000,
 'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
