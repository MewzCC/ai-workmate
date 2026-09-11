ALTER TABLE integration_invocation
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_integration_invocation_runtime
    ON integration_invocation(tenant_id, created_at DESC, id DESC);

CREATE OR REPLACE VIEW runtime_log_view AS
SELECT 'INTEGRATION'::VARCHAR(16) AS source,
       invocation.id AS log_id,
       invocation.tenant_id,
       endpoint.endpoint_code AS reference_code,
       CONCAT(endpoint.http_method, ' ', endpoint.relative_path) AS operation,
       CASE invocation.outcome WHEN 'SUCCESS' THEN 'SUCCEEDED' ELSE 'FAILED' END::VARCHAR(24) AS outcome,
       NULL::VARCHAR(16) AS decision,
       NULL::VARCHAR(80) AS decision_code,
       invocation.http_status AS status_code,
       invocation.duration_ms,
       invocation.operator_id,
       invocation.operator_label,
       invocation.trace_id,
       invocation.request_hash AS request_fingerprint,
       invocation.response_preview AS detail_preview,
       invocation.error_code,
       NULL::BOOLEAN AS handler_invoked,
       NULL::INTEGER AS result_bytes,
       NULL::INTEGER AS attempt,
       invocation.created_at AS started_at,
       invocation.created_at AS completed_at
FROM integration_invocation invocation
JOIN integration_endpoint endpoint
  ON endpoint.id = invocation.endpoint_id
 AND endpoint.tenant_id = invocation.tenant_id
UNION ALL
SELECT 'AGENT'::VARCHAR(16) AS source,
       invocation.id AS log_id,
       invocation.tenant_id,
       task.task_no AS reference_code,
       invocation.tool_code AS operation,
       CASE
           WHEN invocation.outcome IS NOT NULL THEN invocation.outcome
           WHEN invocation.decision = 'ALLOW' THEN 'RUNNING'
           ELSE 'REJECTED'
       END::VARCHAR(24) AS outcome,
       invocation.decision,
       invocation.decision_code,
       NULL::INTEGER AS status_code,
       invocation.duration_ms,
       invocation.user_id AS operator_id,
       COALESCE(NULLIF(user_account.display_name, ''), user_account.username) AS operator_label,
       invocation.trace_id,
       invocation.args_hash AS request_fingerprint,
       invocation.args_summary AS detail_preview,
       invocation.error_class AS error_code,
       invocation.handler_invoked,
       invocation.result_bytes,
       invocation.attempt,
       invocation.started_at,
       invocation.completed_at
FROM agent_tool_invocation invocation
JOIN agent_task task
  ON task.id = invocation.task_id
 AND task.tenant_id = invocation.tenant_id
 AND task.user_id = invocation.user_id
JOIN app_user user_account
  ON user_account.id = invocation.user_id
 AND user_account.tenant_id = invocation.tenant_id;

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'runtime-log:read', '查看运行日志', '开放平台',
       '查看租户内受控接口和 Agent 工具的脱敏运行记录', tenant.id
FROM tenant
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, 'runtime-log:read', role.tenant_id
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'RUNTIME_LOGS', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'runtime-logs';
