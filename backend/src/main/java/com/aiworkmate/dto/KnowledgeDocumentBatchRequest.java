package com.aiworkmate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocumentBatchRequest(
        @NotEmpty(message = "{validation.kb.batch.notEmpty}")
        @Size(max = 100, message = "{validation.kb.batch.maxSize}")
        List<@NotNull(message = "{validation.kb.docId.notNull}") Long> ids
) {
}
