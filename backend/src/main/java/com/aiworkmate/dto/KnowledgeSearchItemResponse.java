package com.aiworkmate.dto;

public record KnowledgeSearchItemResponse(
        Long docId,
        Long chunkId,
        String filename,
        int chunkIndex,
        String content,
        double score
) {
}
