package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.KnowledgeContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private ChatClient chatClient;
    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private KnowledgeContextService knowledgeContextService;
    @Mock private AttachmentService attachmentService;
    @Mock private AiRuntimeProperties aiRuntimeProperties;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void shouldRejectChatWhenProviderKeyIsNotConfigured() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(aiRuntimeProperties.configured()).thenReturn(false);

        assertThatThrownBy(() -> chatService.chat(
                1001L, "USER", 2001L, "你好", "deepseek-chat", List.of(), 10))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CHAT_UNAVAILABLE.getErrorCode()));
    }
}
