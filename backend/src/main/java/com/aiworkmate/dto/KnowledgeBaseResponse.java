package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(
        Long id,
        String name,
        String icon,
        String description,
        long docCount,
        long chunkCount,
        String embeddingProvider,
        String embeddingModel,
        String rerankModel,
        int chunkSize,
        int chunkOverlap,
        int denseTopK,
        int sparseTopK,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
