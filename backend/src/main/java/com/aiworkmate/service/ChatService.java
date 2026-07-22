package com.aiworkmate.service;

import reactor.core.publisher.Flux;
import com.aiworkmate.service.model.ChatChunk;

import java.util.List;

public interface ChatService {

    /**
     * 流式对话 — 返回 SSE 流，前端逐字渲染
     */
    Flux<ChatChunk> chatStream(Long userId, String role, Long conversationId, String message,
                               String model, List<Long> attachmentIds, int maxContextRounds);

    /**
     * 非流式对话 — 一次性返回
     */
    String chat(Long userId, String role, Long conversationId, String message,
                String model, List<Long> attachmentIds, int maxContextRounds);
}
