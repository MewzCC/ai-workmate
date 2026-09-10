package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetPageResponse(List<BudgetResponse> records, long total, int page, int size,
        Stats stats, boolean canManage) {
    public record Stats(long totalPlans, long activePlans, long warningPlans, BigDecimal totalAmount,
                        BigDecimal occupiedAmount, BigDecimal spentAmount, BigDecimal availableAmount) {}
}
