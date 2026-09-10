package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record SupplierStatusHistoryResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String reason,
        String operatorLabel,
        LocalDateTime createdAt
) {
}
