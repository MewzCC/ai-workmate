package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.MessageResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<ConversationResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.listConversations(user.userId()));
    }

    @GetMapping("/{conversationId}/messages")
    public Result<List<MessageResponse>> messages(@PathVariable Long conversationId,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.listMessages(user.userId(), conversationId));
    }
}
