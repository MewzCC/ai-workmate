package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        Long messageId,
        String type,
        String name,
        Long size,
        String mimeType,
        String contentUrl,
        boolean parsed,
        LocalDateTime createdAt
) {
}
