package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record DictionaryTypeResponse(Long id, String code, String name, String description,
                                     String status, Integer sortOrder, Integer itemCount,
                                     Integer activeItemCount, Integer version,
                                     LocalDateTime updatedAt, boolean canManage) {
}
