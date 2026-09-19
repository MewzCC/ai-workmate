package com.aiworkmate.service.model;

import java.math.BigDecimal;

/** Transport-neutral mutable fields accepted by the controlled Agent draft boundary. */
public record BudgetAgentDraftCommand(
        String name,
        int fiscalYear,
        long ownerUserId,
        BigDecimal totalAmount,
        String currency,
        int warningThreshold,
        String summary
) { }
