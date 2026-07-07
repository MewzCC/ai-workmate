package com.aiworkmate.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 — 使用 ChatClient 统一管理 AI 调用
 * 第 1 月：简单对话
 * 第 3 月：Tool Calling / Agent
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
