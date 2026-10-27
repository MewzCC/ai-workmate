INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, 'AI 能力', permission.description, tenant.id
FROM tenant
CROSS JOIN (VALUES
    ('agent:tool:approval.task.approve', 'Agent 通过单个审批', '允许 Agent 在二次确认后通过当前用户负责的单个审批待办'),
    ('agent:tool:approval.task.reject', 'Agent 驳回单个审批', '允许 Agent 在二次确认后驳回当前用户负责的单个审批待办'),
    ('agent:tool:approval.task.transfer', 'Agent 转交单个审批', '允许 Agent 在二次确认后将单个审批待办转交给有效审批人'),
    ('agent:tool:approval.task.copy', 'Agent 抄送单个审批', '允许 Agent 在显式确认后将单个审批待办抄送给有效用户'),
    ('agent:tool:approval.task.addSign', 'Agent 为单个审批加签', '允许 Agent 在二次确认后为单个审批待办增加前签或后签人员')
) AS permission(code, name, description)
WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, module = EXCLUDED.module, description = EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT source.tenant_id, source.role_code, permission.code
FROM rbac_role_permission source
CROSS JOIN (VALUES
    ('agent:tool:approval.task.approve'),
    ('agent:tool:approval.task.reject'),
    ('agent:tool:approval.task.transfer'),
    ('agent:tool:approval.task.copy'),
    ('agent:tool:approval.task.addSign')
) AS permission(code)
WHERE source.permission_code = 'approval:act'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool (
    tenant_id, code, name, description, handler_version, parameters_schema, output_schema,
    schema_hash, risk_level, required_permissions, permission_mode, data_scope_policy,
    retry_policy, side_effect, confirmation_policy, max_result_items, max_result_bytes,
    timeout_ms, audit_level, enabled
) VALUES
(NULL, 'approval.task.approve', 'Approve one assigned task',
 'Approves exactly one currently assigned approval task at its expected version.', '1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}'::jsonb,
 'sha256:a2e3f2c069b001562b3d793ef0e668cdb2a94981e75d58f93a4a69aff05072e7',
 'L2', '["approval:act"]'::jsonb, 'ALL', 'ASSIGNED_TO_SELF', 'NEVER', 'SINGLE_WRITE', 'SECONDARY', 1, 8192, 10000, 'FULL_WRITE_AUDIT', TRUE),
(NULL, 'approval.task.reject', 'Reject one assigned task',
 'Rejects exactly one currently assigned approval task with a bounded reason.', '1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}'::jsonb,
 'sha256:8e41b5e3a8c5e79203293723a5996bdc5f57a66b4740617c318a751783136750',
 'L2', '["approval:act"]'::jsonb, 'ALL', 'ASSIGNED_TO_SELF', 'NEVER', 'SINGLE_WRITE', 'SECONDARY', 1, 8192, 10000, 'FULL_WRITE_AUDIT', TRUE),
(NULL, 'approval.task.transfer', 'Transfer one assigned task',
 'Transfers exactly one assigned task to one eligible approver.', '1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}'::jsonb,
 'sha256:232395ec5be77186482ed6cd4b0148afbce0ca21a7f5f0d0bd18a21a265bf04a',
 'L2', '["approval:act"]'::jsonb, 'ALL', 'ASSIGNED_TO_SELF', 'NEVER', 'SINGLE_WRITE', 'SECONDARY', 1, 8192, 10000, 'FULL_WRITE_AUDIT', TRUE),
(NULL, 'approval.task.copy', 'Copy one assigned task',
 'Sends a read-only copy of one assigned task to one active tenant user.', '1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}'::jsonb,
 'sha256:232395ec5be77186482ed6cd4b0148afbce0ca21a7f5f0d0bd18a21a265bf04a',
 'L1', '["approval:act"]'::jsonb, 'ALL', 'ASSIGNED_TO_SELF', 'NEVER', 'SINGLE_WRITE', 'EXPLICIT', 1, 8192, 10000, 'FULL_WRITE_AUDIT', TRUE),
(NULL, 'approval.task.addSign', 'Add one approval signer',
 'Adds exactly one eligible pre-signer or post-signer to one assigned approval task.', '1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","mode","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"mode":{"type":"string","enum":["PRE","POST"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}'::jsonb,
 'sha256:18d64a13f3cdda6bf7691f6890f0de86b7cacafeca415127720569c81bd44e70',
 'L2', '["approval:act"]'::jsonb, 'ALL', 'ASSIGNED_TO_SELF', 'NEVER', 'SINGLE_WRITE', 'SECONDARY', 1, 8192, 10000, 'FULL_WRITE_AUDIT', TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
