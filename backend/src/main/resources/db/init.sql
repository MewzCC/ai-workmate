-- ============================================
-- AI WorkMate 初始化 SQL
-- ============================================

-- 启用 pgvector 扩展（需要先安装 pgvector 插件）
-- CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(120) NOT NULL UNIQUE,
    display_name VARCHAR(50),
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    avatar      VARCHAR(500),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email ON app_user (LOWER(email)) WHERE email IS NOT NULL;

COMMENT ON TABLE app_user IS '用户表';
COMMENT ON COLUMN app_user.role IS 'USER / ADMIN';
COMMENT ON COLUMN app_user.status IS '1=正常 0=禁用';

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL DEFAULT '新对话',
    model       VARCHAR(50)  NOT NULL DEFAULT 'deepseek-v4-flash',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conv_user_id ON conversation(user_id);

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,  -- user / assistant / system
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'success',
    feedback        VARCHAR(20),
    token_count     INT          DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msg_conv_id ON message(conversation_id);

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

CREATE INDEX idx_attachment_user_conv ON attachment(user_id, conversation_id);
CREATE INDEX idx_attachment_message ON attachment(message_id);

-- 知识库文档表（第2月使用）
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_size   BIGINT       NOT NULL,
    file_type   VARCHAR(20)  NOT NULL,
    chunk_count INT          DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 知识库向量块表（第2月使用，需要 pgvector）
-- CREATE TABLE IF NOT EXISTS knowledge_chunk (
--     id          BIGSERIAL PRIMARY KEY,
--     doc_id      BIGINT       NOT NULL,
--     chunk_index INT          NOT NULL,
--     content     TEXT         NOT NULL,
--     embedding   vector(1536),
--     created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );
