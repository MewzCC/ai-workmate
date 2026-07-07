-- ============================================
-- AI WorkMate 初始化 SQL
-- ============================================

-- 启用 pgvector 扩展（需要先安装 pgvector 插件）
-- CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE IF NOT EXISTS "user" (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    avatar      VARCHAR(500),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE "user" IS '用户表';
COMMENT ON COLUMN "user".role IS 'USER / ADMIN';
COMMENT ON COLUMN "user".status IS '1=正常 0=禁用';

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL DEFAULT '新对话',
    model       VARCHAR(50)  NOT NULL DEFAULT 'deepseek-chat',
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
    token_count     INT          DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msg_conv_id ON message(conversation_id);

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
