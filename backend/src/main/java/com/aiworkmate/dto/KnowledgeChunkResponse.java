package com.aiworkmate.dto;

public record KnowledgeChunkResponse(
        Long vectorId,
        int chunkIndex,
        String content,
        int charCount
) {
}
