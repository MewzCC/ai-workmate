package com.aiworkmate.service;

import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.MessageResponse;
import com.aiworkmate.dto.CreateConversationRequest;

import java.util.List;

public interface ConversationService {

    List<ConversationResponse> listConversations(Long userId, String search);

    ConversationResponse createConversation(Long userId, CreateConversationRequest request);

    ConversationResponse renameConversation(Long userId, Long conversationId, String title);

    void deleteConversation(Long userId, Long conversationId);

    List<MessageResponse> listMessages(Long userId, Long conversationId);

    void updateFeedback(Long userId, Long messageId, String feedback);
}
