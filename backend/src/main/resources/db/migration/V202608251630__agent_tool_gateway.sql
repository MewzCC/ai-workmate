ALTER TABLE agent_task
    ADD COLUMN IF NOT EXISTS lease_token_hash VARCHAR(80);

CREATE TABLE IF NOT EXISTS agent_tool_invocation (
    id                  BIGSERIAL PRIMARY KEY,
    decision_id         VARCHAR(36) NOT NULL,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    task_id             BIGINT NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    step_id             BIGINT NOT NULL REFERENCES agent_task_step(id) ON DELETE CASCADE,
    attempt             INTEGER NOT NULL,
    tool_code           VARCHAR(80) NOT NULL,
    tool_version        VARCHAR(40) NOT NULL,
    decision            VARCHAR(16) NOT NULL,
    decision_code       VARCHAR(80) NOT NULL,
    args_hash           VARCHAR(80) NOT NULL,
    args_summary        VARCHAR(500),
    handler_invoked     BOOLEAN NOT NULL DEFAULT FALSE,
    outcome             VARCHAR(24),
    result_bytes        INTEGER,
    error_class         VARCHAR(80),
    trace_id            VARCHAR(64) NOT NULL,
    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    duration_ms         BIGINT,
    CONSTRAINT uk_agent_tool_invocation_decision UNIQUE (decision_id),
    CONSTRAINT chk_agent_tool_invocation_attempt CHECK (attempt BETWEEN 0 AND 2),
    CONSTRAINT chk_agent_tool_invocation_decision CHECK (
        decision IN ('ALLOW', 'DENY', 'STALE', 'THROTTLED', 'UNAVAILABLE')
    ),
    CONSTRAINT chk_agent_tool_invocation_outcome CHECK (
        outcome IS NULL OR outcome IN ('SUCCEEDED', 'REJECTED', 'FAILED', 'TIMED_OUT', 'RESULT_INVALID')
    ),
    CONSTRAINT chk_agent_tool_invocation_bytes CHECK (result_bytes IS NULL OR result_bytes BETWEEN 0 AND 262144)
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_invocation_actor
    ON agent_tool_invocation(tenant_id, user_id, started_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_invocation_attempt
    ON agent_tool_invocation(task_id, step_id, attempt);

CREATE INDEX IF NOT EXISTS idx_agent_tool_invocation_decision
    ON agent_tool_invocation(decision, started_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_invocation_retention
    ON agent_tool_invocation(started_at, tenant_id, id);
