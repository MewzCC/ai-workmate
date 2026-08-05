package com.aiworkmate.service.impl;

import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.entity.Attachment;
import com.aiworkmate.mapper.AttachmentMapper;
import com.aiworkmate.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentExpiryCleanerTest {

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private UploadProperties properties;

    @InjectMocks
    private AttachmentExpiryCleaner cleaner;

    @Test
    void shouldSkipWhenMaxAgeDaysIsZero() {
        when(properties.getAttachmentMaxAgeDays()).thenReturn(0);

        cleaner.cleanExpiredAttachments();

        verify(attachmentMapper, never()).selectList(any());
        verify(objectStorageService, never()).delete(any());
    }

    @Test
    void shouldSkipWhenMaxAgeDaysIsNegative() {
        when(properties.getAttachmentMaxAgeDays()).thenReturn(-1);

        cleaner.cleanExpiredAttachments();

        verify(attachmentMapper, never()).selectList(any());
    }

    @Test
    void shouldDeleteExpiredAttachmentsAcrossBatches() {
        when(properties.getAttachmentMaxAgeDays()).thenReturn(30);
        List<Attachment> firstBatch = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            firstBatch.add(attachment((long) index, "chat-attachments/" + index));
        }
        when(attachmentMapper.selectList(any()))
                .thenReturn(firstBatch)
                .thenReturn(List.of());

        cleaner.cleanExpiredAttachments();

        verify(attachmentMapper, times(2)).selectList(any());
        verify(objectStorageService, times(100)).delete(any());
        verify(attachmentMapper, times(100)).deleteById(any(Long.class));
    }

    @Test
    void shouldDeleteNothingWhenNoExpiredAttachment() {
        when(properties.getAttachmentMaxAgeDays()).thenReturn(30);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        cleaner.cleanExpiredAttachments();

        verify(objectStorageService, never()).delete(any());
        verify(attachmentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void shouldContinueOnSingleDeleteFailure() {
        when(properties.getAttachmentMaxAgeDays()).thenReturn(30);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(
                attachment(1L, "chat-attachments/1"),
                attachment(2L, "chat-attachments/2")));

        doThrow(new RuntimeException("minio down")).when(objectStorageService).delete("chat-attachments/1");

        assertThatCode(cleaner::cleanExpiredAttachments).doesNotThrowAnyException();

        verify(objectStorageService).delete("chat-attachments/2");
        verify(attachmentMapper).deleteById(2L);
    }

    private Attachment attachment(Long id, String storageName) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setStorageName(storageName);
        attachment.setCreatedAt(LocalDateTime.now().minusDays(31));
        return attachment;
    }
}
