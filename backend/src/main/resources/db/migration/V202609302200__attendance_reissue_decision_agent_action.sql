ALTER TABLE attendance_reissue
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'attendance:reissue:decide','审批分配给本人的补卡申请','考勤管理',
       '允许审批人批准或驳回分配给本人的待审批补卡申请',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT source.tenant_id,source.role_code,'attendance:reissue:decide'
FROM rbac_role_permission source
WHERE source.permission_code='route:attendance-reissue'
  AND source.role_code IN ('SUPER_ADMIN','SYSTEM_ADMIN','PROCESS_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:attendance.reissue.decide','Agent 审批补卡申请','AI 能力',
       '允许 Agent 经二次确认后批准或驳回一条分配给当前用户的待审批补卡申请',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:attendance.reissue.decide'
FROM rbac_role_permission WHERE permission_code='attendance:reissue:decide'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'attendance.reissue.decide','Decide an assigned attendance correction',
 'Approves or rejects exactly one pending attendance correction assigned to the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["reissueId","version","decision"],"properties":{"reissueId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0},"decision":{"type":"string","enum":["APPROVED","REJECTED"]},"comment":{"type":"string","maxLength":500}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["reissueId","status","version","decidedAt"],"properties":{"reissueId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","REJECTED"]},"version":{"type":"integer","minimum":1},"decidedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:990ee606bb356549b674e8f03b90433b45e4887e081627eed1db950b227659e3','L2','["attendance:reissue:decide"]'::jsonb,'ALL','ASSIGNED_TO_SELF','NEVER',
 'SINGLE_WRITE','SECONDARY',1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
