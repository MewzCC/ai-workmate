package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ChatRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return chatService.chatStream(
                user.userId(),
                request.getConversationId(),
                request.getMessage(),
                request.getModel()
        );
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
}
