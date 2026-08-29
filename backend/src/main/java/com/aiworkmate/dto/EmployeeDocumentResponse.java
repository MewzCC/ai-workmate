package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record EmployeeDocumentResponse(
        Long id,
        Long employeeUserId,
        String documentType,
        String displayName,
        String mimeType,
        Long fileSize,
        Long uploadedByUserId,
        String uploadedByName,
        String contentUrl,
        LocalDateTime createdAt
) {
}
