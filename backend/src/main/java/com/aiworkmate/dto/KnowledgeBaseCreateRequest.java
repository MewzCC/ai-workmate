package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotBlank(message = "{validation.kb.name.notBlank}")
        @Size(max = 80, message = "{validation.kb.name.maxLength}")
        String name,

        @Size(max = 40, message = "{validation.kb.icon.maxLength}")
        String icon,

        @Size(max = 500, message = "{validation.kb.description.maxLength}")
        String description,
        @Size(max = 128) String embeddingModelRef,
        @Size(max = 128) String rerankModelRef
) {
    public KnowledgeBaseCreateRequest(String name, String icon, String description) {
        this(name, icon, description, null, null);
    }
}
