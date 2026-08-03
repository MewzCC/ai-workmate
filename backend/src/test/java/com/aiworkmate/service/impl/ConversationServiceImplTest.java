package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AttachmentResponse;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private ConversationServiceImpl serviceWithObjectMapper() {
        return new ConversationServiceImpl(conversationMapper, messageMapper, attachmentService,
                new ObjectMapper().registerModule(new ParameterNamesModule()));
    }

    @Test
    void shouldRejectConversationOwnedByAnotherUser() {
        assertThatThrownBy(() -> conversationService.listMessages(1001L, 2002L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN.getErrorCode());
                    assertThat(ex.getStatus()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN.getStatus());
                });
        verifyNoInteractions(messageMapper);
    }

    @Test
    void shouldParseCitationsFromStoredMessage() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(attachmentService.listByMessageIds(any())).thenReturn(List.of());

        Message message = new Message();
        message.setId(3001L);
        message.setConversationId(2001L);
        message.setRole("assistant");
        message.setContent("根据手册，年假规则如下。");
        message.setStatus("success");
        message.setCitations("""
                [{"docId":"10","chunkId":"20","source":"handbook.txt","score":0.91,"text":"Annual leave policy"},
                 {"docId":"12","chunkId":"34","source":"spec.txt","score":0.88,"text":"Onboarding checklist"}]
                """);
        when(messageMapper.selectList(any())).thenReturn(List.of(message));

        var responses = serviceWithObjectMapper().listMessages(1001L, 2001L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).citations()).hasSize(2);
        assertThat(responses.get(0).citations().get(0).source()).isEqualTo("handbook.txt");
        assertThat(responses.get(0).citations().get(0).text()).isEqualTo("Annual leave policy");
        assertThat(responses.get(0).citations().get(1).score()).isEqualTo(0.88);
    }

    @Test
    void shouldFallBackToEmptyCitationsWhenJsonIsBroken() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(attachmentService.listByMessageIds(any())).thenReturn(List.of());

        Message message = new Message();
        message.setId(3002L);
        message.setConversationId(2001L);
        message.setRole("assistant");
        message.setContent("回答");
        message.setStatus("success");
        message.setCitations("[{broken json");
        when(messageMapper.selectList(any())).thenReturn(List.of(message));

        var responses = serviceWithObjectMapper().listMessages(1001L, 2001L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).citations()).isEmpty();
    }
}
