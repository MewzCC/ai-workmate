CREATE INDEX IF NOT EXISTS idx_agent_task_terminal_retention
    ON agent_task(tenant_id, created_at, id)
    WHERE status IN (
        'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT',
        'REJECTED', 'EXPIRED', 'CANCELLED'
    );

CREATE INDEX IF NOT EXISTS idx_agent_task_event_tenant_retention
    ON agent_task_event(task_id, created_at, id);
