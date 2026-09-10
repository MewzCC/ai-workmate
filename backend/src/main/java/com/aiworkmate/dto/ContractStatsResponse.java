package com.aiworkmate.dto;

import java.math.BigDecimal;

public record ContractStatsResponse(
        long total,
        long active,
        long expiring,
        long expired,
        BigDecimal outstandingAmount
) {
}
