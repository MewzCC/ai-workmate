ALTER TABLE asset_ledger
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS asset_operation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    asset_id BIGINT NOT NULL REFERENCES asset_ledger(id) ON DELETE RESTRICT,
    operation_type VARCHAR(20) NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    from_department_id BIGINT,
    to_department_id BIGINT,
    from_owner_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    to_owner_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    operator_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_asset_operation_type CHECK (operation_type IN ('CLAIM', 'RETURN', 'TRANSFER'))
);

CREATE INDEX IF NOT EXISTS idx_asset_operation_history
    ON asset_operation(tenant_id, asset_id, created_at DESC, id DESC);
