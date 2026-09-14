CREATE TABLE IF NOT EXISTS integration_replay_job (
    id                        BIGSERIAL PRIMARY KEY,
    tenant_id                 BIGINT NOT NULL REFERENCES tenant(id),
    source_invocation_id      BIGINT NOT NULL REFERENCES integration_invocation(id),
    endpoint_id               BIGINT NOT NULL REFERENCES integration_endpoint(id),
    endpoint_code             VARCHAR(64) NOT NULL,
    endpoint_name             VARCHAR(160) NOT NULL,
    http_method               VARCHAR(10) NOT NULL,
    relative_path             VARCHAR(500) NOT NULL,
    baseline_request_hash     VARCHAR(64) NOT NULL,
    baseline_outcome          VARCHAR(16) NOT NULL,
    baseline_http_status      INTEGER,
    baseline_response_hash    VARCHAR(64),
    baseline_response_preview TEXT,
    status                    VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    replay_http_status        INTEGER,
    replay_duration_ms        BIGINT,
    replay_response_hash      VARCHAR(64),
    replay_response_preview   TEXT,
    replay_error_code         VARCHAR(60),
    comparison_result         VARCHAR(16),
    trace_id                  VARCHAR(64) NOT NULL,
    reason                    VARCHAR(500) NOT NULL,
    idempotency_key           VARCHAR(64) NOT NULL,
    requested_by              BIGINT NOT NULL REFERENCES app_user(id),
    requested_by_label        VARCHAR(160) NOT NULL,
    started_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at              TIMESTAMP,
    CONSTRAINT ck_integration_replay_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_integration_replay_comparison
        CHECK (comparison_result IS NULL OR comparison_result IN ('MATCHED', 'CHANGED')),
    CONSTRAINT ck_integration_replay_baseline_preview
        CHECK (baseline_response_preview IS NULL OR octet_length(baseline_response_preview) <= 65536),
    CONSTRAINT ck_integration_replay_response_preview
        CHECK (replay_response_preview IS NULL OR octet_length(replay_response_preview) <= 65536)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_replay_idempotency
    ON integration_replay_job(tenant_id, requested_by, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_integration_replay_tenant_time
    ON integration_replay_job(tenant_id, started_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_integration_replay_source
    ON integration_replay_job(tenant_id, source_invocation_id, started_at DESC);

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT permission.code, permission.name, permission.module, permission.description, tenant.id
FROM tenant
CROSS JOIN (VALUES
    ('integration:replay:read', '查看沙箱回放', '开放平台', '查看租户内受控接口回放及脱敏差异'),
    ('integration:replay:execute', '执行沙箱回放', '开放平台', '基于历史调用指纹执行单次受控沙箱回放')
) AS permission(code, name, module, description)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role.code, permission.code, role.tenant_id
FROM rbac_role role
CROSS JOIN (VALUES
    ('integration:replay:read'),
    ('integration:replay:execute')
) AS permission(code)
WHERE role.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'SANDBOX_REPLAY', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'sandbox-replay';
