package com.aiworkmate.dto;

/** Internal bounded create command used by the controlled Agent adapter. */
public record DictionaryItemAgentCreateRequest(
        String value,
        String label,
        String description,
        Integer sortOrder
) {
}
