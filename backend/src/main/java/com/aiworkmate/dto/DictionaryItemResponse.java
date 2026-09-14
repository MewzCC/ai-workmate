package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record DictionaryItemResponse(Long id, Long dictionaryTypeId, String value, String label,
                                     String description, String status, Integer sortOrder,
                                     Long usageCount, Integer version, LocalDateTime updatedAt,
                                     boolean canManage, boolean canDelete) {
}
