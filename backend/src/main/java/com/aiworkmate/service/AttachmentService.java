package com.aiworkmate.service;

import com.aiworkmate.dto.AttachmentResponse;
import com.aiworkmate.entity.Attachment;
import com.aiworkmate.service.model.AttachmentContent;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.util.List;

public interface AttachmentService {

    AttachmentResponse upload(Long userId, Long conversationId, MultipartFile file);

    AttachmentContent loadContent(Long userId, Long attachmentId);

    List<Attachment> requireOwned(Long userId, Long conversationId, List<Long> attachmentIds);

    void bindToMessage(List<Attachment> attachments, Long messageId);

    List<AttachmentResponse> listByMessageIds(List<Long> messageIds);

    void deleteConversationAttachments(Long userId, Long conversationId);

    Resource resourceFor(Attachment attachment);
}
