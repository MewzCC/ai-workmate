INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,'AI 能力',permission.description,tenant.id
FROM tenant
CROSS JOIN (VALUES
 ('agent:tool:hr.change.approve','Agent 通过员工变动申请','允许 Agent 经二次确认后通过当前用户待审批的一笔员工变动申请'),
 ('agent:tool:hr.change.reject','Agent 驳回员工变动申请','允许 Agent 经二次确认并填写理由后驳回当前用户待审批的一笔员工变动申请'),
 ('agent:tool:hr.change.withdraw','Agent 撤回员工变动申请','允许 Agent 经明确确认后撤回当前用户发起的一笔待审批员工变动申请')
) AS permission(code,name,description)
WHERE tenant.code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT grant_row.tenant_id,grant_row.role_code,permission.code
FROM rbac_role_permission grant_row
CROSS JOIN (VALUES
 ('agent:tool:hr.change.approve'),
 ('agent:tool:hr.change.reject'),
 ('agent:tool:hr.change.withdraw')
) AS permission(code)
WHERE grant_row.permission_code='hr:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES
(NULL,'hr.change.approve','Approve one employee change',
 'Approves one pending employee change assigned to the authenticated reviewer.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["changeId","expectedVersion"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0},"comment":{"type":"string","maxLength":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["changeId","status","version"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:9525bd2d59e4ae2eb5195a0ffda94a19d53c4c73236a5122b5eb7424c418761b','L2','["hr:manage"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'hr.change.reject','Reject one employee change',
 'Rejects one pending employee change assigned to the authenticated reviewer.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["changeId","expectedVersion","comment"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0},"comment":{"type":"string","minLength":1,"maxLength":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["changeId","status","version"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:92118412e490ebdd270eceb4ba99b0a313c460710e978aa740dd2d7f968e7870','L2','["hr:manage"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER','SINGLE_WRITE','SECONDARY',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE),
(NULL,'hr.change.withdraw','Withdraw my employee change application',
 'Withdraws one pending employee change application created by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["changeId","expectedVersion"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["changeId","status","version"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":1}}}'::jsonb,
 'sha256:b08cddab012490cf1ebaebc02b1a94513e73f1e29a4528b5968e9f4d27e4d247','L1','["hr:manage"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
