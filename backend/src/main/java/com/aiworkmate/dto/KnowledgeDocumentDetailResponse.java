package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeDocumentDetailResponse(
        Long id,
        String filename,
        long fileSize,
        String fileType,
        int chunkCount,
        String status,
        String embeddingProvider,
        String embeddingModel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<KnowledgeChunkResponse> chunks
) {
}
