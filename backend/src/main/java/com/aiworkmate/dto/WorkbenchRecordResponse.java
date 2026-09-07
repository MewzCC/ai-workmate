package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkbenchRecordResponse(
        Long id,
        String moduleKey,
        String code,
        String title,
        String category,
        String status,
        BigDecimal amount,
        String owner,
        String details,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canManage
) {
}
