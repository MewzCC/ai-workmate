package com.aiworkmate.dto;

/** Internal bounded update command used by the controlled Agent adapter. */
public record DictionaryTypeAgentUpdateRequest(
        Integer version,
        String name,
        String description,
        Integer sortOrder
) {
}
