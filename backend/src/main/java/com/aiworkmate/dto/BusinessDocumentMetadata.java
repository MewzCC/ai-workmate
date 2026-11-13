package com.aiworkmate.dto;

import java.time.LocalDateTime;

/** Read-only business metadata: no storage coordinates, content URL, uploader identity or file bytes. */
public record BusinessDocumentMetadata(String documentId, String documentType, String displayName,
        String mimeType, long fileSize, LocalDateTime createdAt) { }
