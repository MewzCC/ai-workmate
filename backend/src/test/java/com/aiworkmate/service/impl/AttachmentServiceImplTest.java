package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.mapper.AttachmentMapper;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.service.FileParserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private FileParserService fileParserService;

    @Mock
    private UploadProperties properties;

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @Test
    void shouldRejectAttachmentIdsOutsideCurrentUserConversation() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> attachmentService.requireOwned(1001L, 2001L, List.of(3001L)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN.getErrorCode()));
    }
}
