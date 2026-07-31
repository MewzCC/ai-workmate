package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(
        Long id,
        String filename,
        long fileSize,
        String fileType,
        int chunkCount,
        String status,
        String embeddingProvider,
        String embeddingModel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
