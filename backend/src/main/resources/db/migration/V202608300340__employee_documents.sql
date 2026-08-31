CREATE TABLE IF NOT EXISTS employee_document (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    document_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_employee_document_type CHECK (document_type IN ('CONTRACT', 'PROFILE')),
    CONSTRAINT ck_employee_document_size CHECK (file_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_employee_document_employee
    ON employee_document(tenant_id, employee_user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_document_storage_key
    ON employee_document(storage_key);
