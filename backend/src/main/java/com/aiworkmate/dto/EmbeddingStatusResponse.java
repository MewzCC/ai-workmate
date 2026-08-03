package com.aiworkmate.dto;

public record EmbeddingStatusResponse(
        boolean enabled,
        String provider,
        String model,
        int dimension,
        boolean rerankEnabled,
        String rerankModel
) {
}
