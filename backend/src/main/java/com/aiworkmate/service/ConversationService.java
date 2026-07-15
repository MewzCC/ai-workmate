package com.aiworkmate.service;

import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.MessageResponse;

import java.util.List;

public interface ConversationService {

    List<ConversationResponse> listConversations(Long userId);

    List<MessageResponse> listMessages(Long userId, Long conversationId);
}
