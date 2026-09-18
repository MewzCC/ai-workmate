ALTER TABLE employee_change
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_change_agent_key
    ON employee_change(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:hr.change.apply','Agent 提交员工变动申请','AI 能力',
       '允许 Agent 在显式确认后提交一条员工变动审批申请',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:hr.change.apply'
FROM rbac_role_permission WHERE permission_code='hr:manage'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'hr.change.apply','Submit one employee change application',
 'Creates one approval-bound employee change application in the authenticated tenant.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["employeeUserId","changeType","effectiveDate","reviewApproverUserId","reason"],"properties":{"employeeUserId":{"type":"integer","minimum":1},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"effectiveDate":{"type":"string","format":"date"},"targetDepartmentId":{"type":"integer","minimum":1},"targetPositionId":{"type":"integer","minimum":1},"targetSupervisorUserId":{"type":"integer","minimum":1},"reviewApproverUserId":{"type":"integer","minimum":1},"reason":{"type":"string","minLength":1,"maxLength":1000}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["changeId","status","version","submittedAt"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","const":0},"submittedAt":{"type":"string","format":"date-time"}}}'::jsonb,
 'sha256:958d37b7a487f4ac1f736907a017678b20fc27afa341205a5f7b7ab93ef82303',
 'L1','["hr:manage"]'::jsonb,'ALL','TENANT_SCOPED','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,8192,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
