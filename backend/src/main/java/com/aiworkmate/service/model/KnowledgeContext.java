package com.aiworkmate.service.model;

import java.util.List;

public record KnowledgeContext(String promptContext, List<Reference> references) {

    public static KnowledgeContext empty() {
        return new KnowledgeContext("", List.of());
    }

    public boolean hasContext() {
        return promptContext != null && !promptContext.isBlank();
    }

    public record Reference(String docId, String chunkId, String source, double score) {
    }
}
