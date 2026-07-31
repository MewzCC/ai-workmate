package com.aiworkmate.dto;

import java.util.List;

public record KnowledgeSearchResponse(
        String provider,
        String model,
        int dimension,
        List<KnowledgeSearchItemResponse> records
) {
}
