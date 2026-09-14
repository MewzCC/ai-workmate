package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetTransactionResponse(Long id, String type, BigDecimal amount, BigDecimal occupiedBefore,
        BigDecimal occupiedAfter, BigDecimal spentBefore, BigDecimal spentAfter, String referenceCode,
        String note, String operatorLabel, LocalDateTime createdAt) {}
