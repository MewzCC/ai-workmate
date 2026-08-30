package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record SealUsageDocumentResponse(
        Long id,
        Long sealUsageId,
        String displayName,
        String mimeType,
        Long fileSize,
        Long uploadedByUserId,
        String uploadedByName,
        String contentUrl,
        LocalDateTime createdAt
) {
}
