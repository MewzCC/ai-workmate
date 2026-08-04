package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank(message = "{validation.kb.query.notBlank}")
        @Size(max = 2000, message = "{validation.kb.query.maxLength}")
        String query,

        @Min(value = 1, message = "{validation.kb.topK.min}")
        @Max(value = 20, message = "{validation.kb.topK.max}")
        Integer topK,

        @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "{validation.kb.minScore.min}")
        @jakarta.validation.constraints.DecimalMax(value = "1.0", message = "{validation.kb.minScore.max}")
        Double minScore
) {
}
