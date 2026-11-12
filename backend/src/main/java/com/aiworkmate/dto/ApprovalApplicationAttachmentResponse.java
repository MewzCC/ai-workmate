package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record ApprovalApplicationAttachmentResponse(String id, String fieldName, String fieldType,
        String displayName, String mimeType, long fileSize, LocalDateTime createdAt) {
}
