package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @Size(max = 80, message = "{validation.kb.name.maxLength}")
        String name,

        @Size(max = 40, message = "{validation.kb.icon.maxLength}")
        String icon,

        @Size(max = 500, message = "{validation.kb.description.maxLength}")
        String description,

        @Min(value = 100, message = "{validation.kb.chunkSize.min}")
        @Max(value = 8000, message = "{validation.kb.chunkSize.max}")
        Integer chunkSize,

        @Min(value = 0, message = "{validation.kb.chunkOverlap.min}")
        @Max(value = 4000, message = "{validation.kb.chunkOverlap.max}")
        Integer chunkOverlap,

        @Min(value = 1, message = "{validation.kb.denseTopK.min}")
        @Max(value = 50, message = "{validation.kb.denseTopK.max}")
        Integer denseTopK,

        @Min(value = 0, message = "{validation.kb.sparseTopK.min}")
        @Max(value = 50, message = "{validation.kb.sparseTopK.max}")
        Integer sparseTopK
) {
}
