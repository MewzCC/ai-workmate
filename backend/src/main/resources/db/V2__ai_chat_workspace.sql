-- Apply once to an existing PostgreSQL database created before AI Chat Workspace.
ALTER TABLE message ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'success';
ALTER TABLE message ADD COLUMN IF NOT EXISTS feedback VARCHAR(20);

CREATE TABLE IF NOT EXISTS attachment (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    message_id      BIGINT,
    type            VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    storage_name    VARCHAR(100) NOT NULL UNIQUE,
    size            BIGINT       NOT NULL,
    mime_type       VARCHAR(150) NOT NULL,
    extracted_text  TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attachment_user_conv ON attachment(user_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_attachment_message ON attachment(message_id);
