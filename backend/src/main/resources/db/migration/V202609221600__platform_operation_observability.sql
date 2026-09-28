CREATE TABLE IF NOT EXISTS platform_operation_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id         BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    actor_label     VARCHAR(160) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    http_method     VARCHAR(12),
    request_path    VARCHAR(300),
    outcome         VARCHAR(24) NOT NULL,
    status_code     INTEGER,
    duration_ms     BIGINT,
    client_ip       VARCHAR(64),
    user_agent      VARCHAR(300),
    request_id      VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(64) NOT NULL,
    error_code      VARCHAR(80),
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT chk_platform_operation_actor CHECK (
        (tenant_id IS NULL AND user_id IS NULL) OR (tenant_id IS NOT NULL AND user_id IS NOT NULL)
    ),
    CONSTRAINT chk_platform_operation_event CHECK (
        event_type IN ('LOGIN', 'HTTP_READ', 'HTTP_WRITE', 'LOGOUT')
    ),
    CONSTRAINT chk_platform_operation_outcome CHECK (
        outcome IN ('SUCCEEDED', 'REJECTED', 'FAILED')
    ),
    CONSTRAINT chk_platform_operation_duration CHECK (
        duration_ms IS NULL OR duration_ms >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_platform_operation_tenant_time
    ON platform_operation_log(tenant_id, started_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_platform_operation_user_time
    ON platform_operation_log(tenant_id, user_id, started_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_platform_operation_event_time
    ON platform_operation_log(event_type, started_at DESC, id DESC);

CREATE OR REPLACE VIEW runtime_log_view AS
SELECT 'HUMAN'::VARCHAR(16) AS source,
       operation.id AS log_id,
       operation.tenant_id,
       operation.request_id AS reference_code,
       CONCAT_WS(' ', operation.http_method, operation.request_path) AS operation,
       operation.outcome,
       NULL::VARCHAR(16) AS decision,
       NULL::VARCHAR(80) AS decision_code,
       operation.status_code,
       operation.duration_ms,
       operation.user_id AS operator_id,
       operation.actor_label AS operator_label,
       operation.trace_id,
       NULL::VARCHAR(80) AS request_fingerprint,
       CONCAT('IP ', COALESCE(operation.client_ip, '-'), ' · ', COALESCE(operation.user_agent, '-'))::TEXT AS detail_preview,
       operation.error_code,
       NULL::BOOLEAN AS handler_invoked,
       NULL::INTEGER AS result_bytes,
       NULL::INTEGER AS attempt,
       operation.started_at,
       operation.completed_at,
       'HUMAN'::VARCHAR(16) AS actor_type,
       operation.event_type,
       operation.client_ip,
       operation.user_agent
FROM platform_operation_log operation
UNION ALL
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
       invocation.created_at AS completed_at,
       'SYSTEM'::VARCHAR(16) AS actor_type,
       'INTEGRATION_CALL'::VARCHAR(32) AS event_type,
       NULL::VARCHAR(64) AS client_ip,
       NULL::VARCHAR(300) AS user_agent
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
       invocation.completed_at,
       'AI_AGENT'::VARCHAR(16) AS actor_type,
       'TOOL_CALL'::VARCHAR(32) AS event_type,
       NULL::VARCHAR(64) AS client_ip,
       NULL::VARCHAR(300) AS user_agent
FROM agent_tool_invocation invocation
JOIN agent_task task
  ON task.id = invocation.task_id
 AND task.tenant_id = invocation.tenant_id
 AND task.user_id = invocation.user_id
JOIN app_user user_account
  ON user_account.id = invocation.user_id
 AND user_account.tenant_id = invocation.tenant_id;
