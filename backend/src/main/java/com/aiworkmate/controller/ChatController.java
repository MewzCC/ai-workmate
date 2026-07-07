package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ChatRequest;
import com.aiworkmate.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI 对话控制器 — 支持 SSE 流式输出
 *
 * POST /api/chat/stream  → SSE 流式（打字机效果）
 * POST /api/chat         → 非流式（一次性返回）
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话 — 返回 text/event-stream
     * 前端使用 EventSource 或 fetch + ReadableStream 消费
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request,
                                   HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Flux.error(new IllegalArgumentException("未登录"));
        }

        return chatService.chatStream(
                userId,
                request.getConversationId(),
                request.getMessage(),
                request.getModel()
        );
    }

    /**
     * 非流式对话
     */
    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequest request,
                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        String response = chatService.chat(
                userId,
                request.getConversationId(),
                request.getMessage(),
                request.getModel()
        );
        return Result.ok(response);
    }
}
