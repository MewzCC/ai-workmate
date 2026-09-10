package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContractEventResponse(
        Long id,
        String eventType,
        String fromValue,
        String toValue,
        BigDecimal amount,
        String detail,
        String operatorLabel,
        LocalDateTime createdAt
) {
}
