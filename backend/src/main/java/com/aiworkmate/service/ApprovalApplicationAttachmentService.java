package com.aiworkmate.service;

import com.aiworkmate.dto.ApprovalApplicationAttachmentResponse;
import com.aiworkmate.service.model.AttachmentContent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ApprovalApplicationAttachmentService {
    ApprovalApplicationAttachmentResponse upload(Long userId, Long applicationId, String fieldName, MultipartFile file);
    ApprovalApplicationAttachmentResponse metadata(Long userId, Long applicationId, Long attachmentId, Long taskId);
    AttachmentContent content(Long userId, Long applicationId, Long attachmentId, Long taskId);
    void validateReferences(Long tenantId, Long applicationId, String fieldName, String fieldType, List<?> references);
}
