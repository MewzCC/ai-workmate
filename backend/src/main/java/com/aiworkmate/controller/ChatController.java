package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.ChatRequest;
import com.aiworkmate.dto.ChatStreamEvent;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(@Valid @RequestBody ChatRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        String requestId = TraceContext.requestId();
        String traceId = TraceContext.traceId();
        Flux<ServerSentEvent<ChatStreamEvent>> stream = chatService.chatStream(
                user.userId(),
                request.getConversationId(),
                request.getMessage(),
                request.getModel()
        ).map(chunk -> event("delta", ChatStreamEvent.delta(chunk, requestId, traceId)));

        return stream
                .concatWithValues(event("done", ChatStreamEvent.done(requestId, traceId)))
                .onErrorResume(ex -> Flux.just(event("error", ChatStreamEvent.error(
                        ErrorCode.AI_CHAT_UNAVAILABLE.getDefaultMessage(),
                        ErrorCode.AI_CHAT_UNAVAILABLE.getErrorCode(),
                        requestId,
                        traceId
                ))));
    }

    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        String response = chatService.chat(
                user.userId(),
                request.getConversationId(),
                request.getMessage(),
                request.getModel()
        );
        return Result.ok(response);
    }

    private ServerSentEvent<ChatStreamEvent> event(String type, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder(data).event(type).build();
    }
}
