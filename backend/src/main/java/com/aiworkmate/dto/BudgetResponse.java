package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BudgetResponse(Long id, String code, String name, Integer fiscalYear, Long ownerUserId,
        String ownerLabel, BigDecimal totalAmount, BigDecimal occupiedAmount, BigDecimal spentAmount,
        BigDecimal availableAmount, String currency, Integer warningThreshold, int utilizationPercent,
        String alertLevel, String status, String summary, Integer version, LocalDateTime updatedAt,
        boolean canManage, List<String> allowedTransitions) {}
