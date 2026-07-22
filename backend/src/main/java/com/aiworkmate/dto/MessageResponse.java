package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        String role,
        String content,
        String status,
        String feedback,
        List<AttachmentResponse> attachments,
        LocalDateTime createdAt
) {
}
