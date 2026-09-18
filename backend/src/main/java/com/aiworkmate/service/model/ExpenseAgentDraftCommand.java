package com.aiworkmate.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseAgentDraftCommand(
        BigDecimal amount,
        String category,
        LocalDate expenseDate,
        String invoiceNumber,
        String reason
) { }
