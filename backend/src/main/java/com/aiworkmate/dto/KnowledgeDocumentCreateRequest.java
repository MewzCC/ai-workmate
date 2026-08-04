package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentCreateRequest(
        @NotNull(message = "{validation.kb.id.notNull}")
        Long kbId,

        @NotBlank(message = "{validation.kb.filename.notBlank}")
        @Size(max = 255, message = "{validation.kb.filename.maxLength}")
        String filename,

        @NotBlank(message = "{validation.kb.content.notBlank}")
        @Size(max = 120000, message = "{validation.kb.content.maxLength}")
        String content
) {
}
