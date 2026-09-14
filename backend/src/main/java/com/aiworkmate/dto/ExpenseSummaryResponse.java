package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseSummaryResponse(
        Long id, String title, BigDecimal amount, String category, LocalDate expenseDate,
        String invoiceNumber, String reason, String status, Integer version, String approverName,
        LocalDateTime dueAt, LocalDateTime submittedAt, boolean overdue, boolean canRemind,
        boolean canWithdraw, boolean canEditDraft, boolean canCancel) { }
