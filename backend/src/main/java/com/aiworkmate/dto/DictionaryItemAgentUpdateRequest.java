package com.aiworkmate.dto;

/** Internal bounded update command used by the controlled Agent adapter. */
public record DictionaryItemAgentUpdateRequest(
        Integer version,
        String label,
        String description,
        Integer sortOrder
) {
}
