CREATE UNIQUE INDEX IF NOT EXISTS ux_approval_application_attachment_owner
    ON approval_application (tenant_id, id, applicant_user_id);

CREATE TABLE IF NOT EXISTS approval_application_attachment (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    field_type VARCHAR(12) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_attachment_application_owner
        FOREIGN KEY (tenant_id, application_id, owner_user_id)
        REFERENCES approval_application (tenant_id, id, applicant_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_approval_attachment_field_type CHECK (field_type IN ('file', 'image')),
    CONSTRAINT ck_approval_attachment_file_size CHECK (file_size > 0),
    CONSTRAINT ck_approval_attachment_image_type CHECK (
        field_type <> 'image' OR mime_type IN ('image/jpeg', 'image/png', 'image/webp')
    )
);

CREATE INDEX IF NOT EXISTS idx_approval_attachment_application_field
    ON approval_application_attachment (tenant_id, application_id, field_name, id);
