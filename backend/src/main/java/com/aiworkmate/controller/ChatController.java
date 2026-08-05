package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.MessageUtils;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ChatRequest;
import com.aiworkmate.dto.ChatStreamEvent;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    /** 与 {@code LocaleConfig} 保持一致：仅支持 zh-CN / en-US */
    private static final List<Locale> SUPPORTED_LANGUAGES = List.of(Locale.SIMPLIFIED_CHINESE, Locale.US);

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String requestId = TraceContext.requestId();
        String traceId = TraceContext.traceId();
        Flux<ServerSentEvent<ChatStreamEvent>> stream = chatService.chatStream(
                user.userId(),
                user.role(),
                request.getConversationId(),
                request.getMessage(),
                request.getModel(),
                request.getKbId(),
                request.getAttachmentIds(),
                request.getMaxContextRounds(),
                resolveLanguage(acceptLanguage)
        ).map(chunk -> event(chunk.type(), ChatStreamEvent.chunk(chunk.type(), chunk.data(),
                chunk.messageId(), chunk.conversationId(), requestId, traceId)));

        return stream
                .concatWithValues(event("done", ChatStreamEvent.done(requestId, traceId)))
                .onErrorResume(ex -> Flux.just(event("error", ChatStreamEvent.error(
                        ex instanceof BusinessException businessException
                                ? businessException.getMessage()
                                : MessageUtils.resolve(ErrorCode.AI_CHAT_UNAVAILABLE.getMessageKey()),
                        ErrorCode.AI_CHAT_UNAVAILABLE.getErrorCode(),
                        requestId,
                        traceId
                ))));
    }

    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequest request,
                               @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        String response = chatService.chat(
                user.userId(),
                user.role(),
                request.getConversationId(),
                request.getMessage(),
                request.getModel(),
                request.getKbId(),
                request.getAttachmentIds(),
                request.getMaxContextRounds(),
                resolveLanguage(acceptLanguage)
        );
        return Result.ok(response);
    }

    /**
     * 将请求头 Accept-Language 规范化为受支持的界面语言；缺省或无法解析时回退 zh-CN。
     */
    private Locale resolveLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        try {
            Locale matched = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED_LANGUAGES);
            return matched != null ? matched : Locale.SIMPLIFIED_CHINESE;
        } catch (IllegalArgumentException ex) {
            return Locale.SIMPLIFIED_CHINESE;
        }
    }

    private ServerSentEvent<ChatStreamEvent> event(String type, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder(data).event(type).build();
    }
}
