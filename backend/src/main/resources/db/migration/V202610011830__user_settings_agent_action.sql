INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'settings:self:update','更新个人系统设置','系统设置',
       '允许当前用户更新自己的模型、上下文、流式输出与 OCR 偏好',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'settings:self:update'
FROM rbac_role_permission WHERE permission_code='route:system-config'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:userSettings.update','Agent 更新个人系统设置','AI 能力',
       '允许 Agent 经明确确认后更新当前用户自己的安全偏好',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:userSettings.update'
FROM rbac_role_permission WHERE permission_code='settings:self:update'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'userSettings.update','Update my personal AI settings',
 'Updates the authenticated user''s model, context, streaming and OCR preferences.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["model","maxContextRounds","stream","forcePdfOcr"],"properties":{"model":{"type":"string","enum":["deepseek-v4-flash","deepseek-v4-pro"]},"maxContextRounds":{"type":"integer","minimum":1,"maximum":20},"stream":{"type":"boolean"},"forcePdfOcr":{"type":"boolean"}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["model","maxContextRounds","stream","forcePdfOcr"],"properties":{"model":{"type":"string","enum":["deepseek-v4-flash","deepseek-v4-pro"]},"maxContextRounds":{"type":"integer","minimum":1,"maximum":20},"stream":{"type":"boolean"},"forcePdfOcr":{"type":"boolean"}}}'::jsonb,
 'sha256:3feb8fb4d05d55598844578f85d61f905114ceb4ac1bca9096f0b06dfbc11e59','L1','["settings:self:update"]'::jsonb,'ALL','SELF','NEVER','SINGLE_WRITE','EXPLICIT',
 1,4096,10000,'FULL_WRITE_AUDIT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
