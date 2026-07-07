package com.aiworkmate.service;

import reactor.core.publisher.Flux;

public interface ChatService {

    /**
     * 流式对话 — 返回 SSE 流，前端逐字渲染
     */
    Flux<String> chatStream(Long userId, Long conversationId, String message, String model);

    /**
     * 非流式对话 — 一次性返回
     */
    String chat(Long userId, Long conversationId, String message, String model);
}
