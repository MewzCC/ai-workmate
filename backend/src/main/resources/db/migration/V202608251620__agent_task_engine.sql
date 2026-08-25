CREATE TABLE IF NOT EXISTS agent_task (
    id                          BIGSERIAL PRIMARY KEY,
    task_no                     VARCHAR(36) NOT NULL,
    tenant_id                   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id                     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    conversation_id             BIGINT REFERENCES conversation(id) ON DELETE SET NULL,
    page_id                     VARCHAR(80) NOT NULL,
    input                       TEXT NOT NULL,
    page_context                JSONB NOT NULL DEFAULT '{}'::jsonb,
    plan                        JSONB,
    plan_hash                   VARCHAR(80),
    plan_version                INTEGER NOT NULL DEFAULT 1,
    max_risk_level              VARCHAR(8) NOT NULL DEFAULT 'L0',
    status                      VARCHAR(32) NOT NULL,
    confirmation_token_hash     VARCHAR(80),
    confirmation_expires_at     TIMESTAMP,
    confirmed_at                TIMESTAMP,
    confirmation_consumed_at    TIMESTAMP,
    timeout_at                  TIMESTAMP,
    worker_id                   VARCHAR(80),
    lease_until                 TIMESTAMP,
    heartbeat_at                TIMESTAMP,
    attempt_count               INTEGER NOT NULL DEFAULT 0,
    planner_model               VARCHAR(120),
    prompt_version              VARCHAR(40),
    planning_latency_ms         BIGINT,
    estimated_tokens            INTEGER,
    tool_call_count             INTEGER NOT NULL DEFAULT 0,
    trace_id                    VARCHAR(64) NOT NULL,
    started_at                  TIMESTAMP,
    finished_at                 TIMESTAMP,
    error_code                  VARCHAR(80),
    error_message               VARCHAR(500),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_task_no UNIQUE (task_no),
    CONSTRAINT chk_agent_task_status CHECK (status IN (
        'RECEIVED', 'PLANNING', 'PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED', 'RUNNING',
        'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'
    )),
    CONSTRAINT chk_agent_task_risk CHECK (max_risk_level IN ('L0', 'L1', 'L2')),
    CONSTRAINT chk_agent_task_attempt CHECK (attempt_count BETWEEN 0 AND 2),
    CONSTRAINT chk_agent_task_calls CHECK (tool_call_count BETWEEN 0 AND 5),
    CONSTRAINT chk_agent_task_plan_version CHECK (plan_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_owner_created
    ON agent_task(tenant_id, user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_agent_task_worker_claim
    ON agent_task(status, lease_until, created_at, id)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX IF NOT EXISTS idx_agent_task_timeout
    ON agent_task(status, timeout_at)
    WHERE status IN ('WAITING_CONFIRMATION', 'QUEUED', 'RUNNING');

CREATE TABLE IF NOT EXISTS agent_task_step (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    sequence_no         INTEGER NOT NULL,
    tool_code           VARCHAR(80) NOT NULL,
    tool_version        VARCHAR(40) NOT NULL,
    schema_hash         VARCHAR(80) NOT NULL,
    args                JSONB NOT NULL,
    args_hash           VARCHAR(80) NOT NULL,
    risk_level          VARCHAR(8) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    result              JSONB,
    result_summary      VARCHAR(1000),
    error_code          VARCHAR(80),
    error_message       VARCHAR(500),
    timeout_at          TIMESTAMP,
    trace_id            VARCHAR(64) NOT NULL,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_task_step_sequence UNIQUE (task_id, sequence_no),
    CONSTRAINT chk_agent_step_sequence CHECK (sequence_no BETWEEN 1 AND 3),
    CONSTRAINT chk_agent_step_risk CHECK (risk_level IN ('L0', 'L1', 'L2')),
    CONSTRAINT chk_agent_step_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED')),
    CONSTRAINT chk_agent_step_attempt CHECK (attempt_count BETWEEN 0 AND 2)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_step_task
    ON agent_task_step(task_id, sequence_no);

CREATE TABLE IF NOT EXISTS agent_task_event (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    event_type          VARCHAR(40) NOT NULL,
    payload             JSONB NOT NULL,
    trace_id            VARCHAR(64) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_event_type CHECK (event_type IN (
        'snapshot', 'step-started', 'step-completed', 'task-completed', 'task-failed', 'heartbeat'
    ))
);

CREATE INDEX IF NOT EXISTS idx_agent_task_event_resume
    ON agent_task_event(task_id, id);

CREATE INDEX IF NOT EXISTS idx_agent_task_event_retention
    ON agent_task_event(created_at, task_id);

CREATE TABLE IF NOT EXISTS agent_idempotency (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    operation           VARCHAR(20) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_hash        VARCHAR(80) NOT NULL,
    task_id             BIGINT NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_idempotency_domain UNIQUE (tenant_id, user_id, operation, idempotency_key),
    CONSTRAINT chk_agent_idempotency_operation CHECK (operation IN ('PLAN', 'EXECUTE')),
    CONSTRAINT chk_agent_idempotency_key CHECK (
        char_length(idempotency_key) BETWEEN 8 AND 128 AND idempotency_key !~ '[[:space:]]'
    )
);

CREATE INDEX IF NOT EXISTS idx_agent_idempotency_task
    ON agent_idempotency(task_id, operation);
