INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'approval:cancel','取消审批草稿','流程审批','允许当前用户取消本人一条未提交审批草稿',tenant.id
FROM tenant WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'approval:cancel'
FROM rbac_role_permission source
WHERE source.permission_code='approval:create'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant CROSS JOIN (VALUES
 ('agent:tool:leave.updateDraft','Agent 修改请假草稿','允许 Agent 在显式确认后修改本人一条请假草稿'),
 ('agent:tool:approval.application.updateDraft','Agent 修改审批草稿','允许 Agent 在显式确认后修改本人一条通用审批草稿'),
 ('agent:tool:approval.application.cancelDraft','Agent 取消审批草稿','允许 Agent 在显式确认后取消本人一条通用审批草稿')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:leave.updateDraft'
FROM rbac_role_permission source WHERE source.permission_code='leave:create'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:approval.application.updateDraft'
FROM rbac_role_permission source WHERE source.permission_code='approval:create'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'agent:tool:approval.application.cancelDraft'
FROM rbac_role_permission source WHERE source.permission_code='approval:cancel'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'leave.updateDraft','Update one leave draft','Replaces bounded fields on one self-owned leave draft.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version","leaveType","startDate","startPeriod","endDate","endPeriod","reason"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"leaveType":{"type":"string","enum":["ANNUAL","PERSONAL","SICK","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","COMPENSATORY","OTHER"]},"approverUserId":{"type":"integer","minimum":1},"startDate":{"type":"string","format":"date","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","format":"date","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:0d86bdad65ea58db352edcf9457669496db8f74ce78f85fd72fcc041eb401bd2','L1','["leave:create"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,16384,15000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'approval.application.updateDraft','Update one approval draft','Replaces schema-validated fields on one self-owned generic approval draft.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version","fields"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"processKey":{"type":"string","minLength":1,"maxLength":64},"fields":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":64},"value":{"type":"string","maxLength":500},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}}},"oneOf":[{"required":["value"],"not":{"required":["values"]}},{"required":["values"],"not":{"required":["value"]}}]}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:a5c42c9496a21a5168c36ccda217610628fe07528ea042342fa483368e445290','L1','["approval:create"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,16384,15000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'approval.application.cancelDraft','Cancel one approval draft','Cancels one self-owned generic approval draft while retaining its audit record.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"CANCELLED"},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:4f107bb62068d65d559bf3c36366f59f30d6ef60d209a4576f3f9480620ca808','L1','["approval:cancel"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',1,4096,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
