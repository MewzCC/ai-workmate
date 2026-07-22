package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.CreateConversationRequest;
import com.aiworkmate.dto.MessageFeedbackRequest;
import com.aiworkmate.dto.MessageResponse;
import com.aiworkmate.dto.RenameConversationRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<ConversationResponse>> list(@RequestParam(required = false) String search,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.listConversations(user.userId(), search));
    }

    @PostMapping
    public Result<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.createConversation(user.userId(), request));
    }

    @PatchMapping("/{conversationId}")
    public Result<ConversationResponse> rename(@PathVariable Long conversationId,
                                               @Valid @RequestBody RenameConversationRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.renameConversation(user.userId(), conversationId, request.title()));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable Long conversationId,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        conversationService.deleteConversation(user.userId(), conversationId);
        return Result.ok();
    }

    @GetMapping("/{conversationId}/messages")
    public Result<List<MessageResponse>> messages(@PathVariable Long conversationId,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(conversationService.listMessages(user.userId(), conversationId));
    }

    @PatchMapping("/messages/{messageId}/feedback")
    public Result<Void> feedback(@PathVariable Long messageId,
                                 @Valid @RequestBody MessageFeedbackRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        conversationService.updateFeedback(user.userId(), messageId, request.feedback());
        return Result.ok();
    }
}
