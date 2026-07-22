package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void shouldRejectConversationOwnedByAnotherUser() {
        assertThatThrownBy(() -> conversationService.listMessages(1001L, 2002L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN.getErrorCode());
                    assertThat(ex.getStatus()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN.getStatus());
                });
        verifyNoInteractions(messageMapper);
    }
}
