ALTER TABLE approval_application
    ADD COLUMN IF NOT EXISTS agent_operation_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_approval_application_agent_operation
    ON approval_application(tenant_id, applicant_user_id, agent_operation_key)
    WHERE agent_operation_key IS NOT NULL;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'approval:create','创建审批申请','流程审批','允许当前用户创建本人审批申请草稿',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'approval:create'
FROM rbac_role_permission WHERE permission_code='route:approval-start'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.application.createDraft','Agent 创建本人审批草稿','AI 能力',
       '允许 Agent 在显式确认后为当前用户创建一条通用审批草稿，不启动审批流程',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.application.createDraft'
FROM rbac_role_permission WHERE permission_code='approval:create'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.application.createDraft','Create my approval application draft',
 'Creates exactly one generic approval draft owned by the authenticated user.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["formKey","fields"],"properties":{"formKey":{"type":"string","minLength":1,"maxLength":64},"processKey":{"type":"string","minLength":1,"maxLength":64},"fields":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":64},"value":{"type":"string","maxLength":500},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}}},"oneOf":[{"required":["value"],"not":{"required":["values"]}},{"required":["values"],"not":{"required":["value"]}}]}}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}'::jsonb,
 'sha256:0b91ec92030a3bdb99baac22dce2b1e7c3a239829740fe9ad174b24ecf340424',
 'L1','["approval:create"]'::jsonb,'ALL','SELF','BUSINESS_IDEMPOTENT','SINGLE_WRITE','EXPLICIT',
 1,16384,15000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
