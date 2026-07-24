-- ============================================
-- AI WorkMate MySQL 建表脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS ai_workmate
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_workmate;

-- 用户表
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(120) NOT NULL UNIQUE,
    display_name VARCHAR(50),
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100) UNIQUE,
    avatar      VARCHAR(500),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL DEFAULT '新对话',
    model       VARCHAR(50)  NOT NULL DEFAULT 'deepseek-chat',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conv_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'success',
    feedback        VARCHAR(20),
    token_count     INT          DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_conv_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attachment (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    message_id      BIGINT,
    type            VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    storage_name    VARCHAR(100) NOT NULL UNIQUE,
    size            BIGINT       NOT NULL,
    mime_type       VARCHAR(150) NOT NULL,
    extracted_text  LONGTEXT,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attachment_user_conv (user_id, conversation_id),
    INDEX idx_attachment_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_size   BIGINT       NOT NULL,
    file_type   VARCHAR(20)  NOT NULL,
    chunk_count INT          DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
