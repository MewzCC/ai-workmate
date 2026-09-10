package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record TenantConfigurationHistoryResponse(
        Long id,
        String category,
        Integer version,
        Long changedBy,
        LocalDateTime createdAt
) {
}
