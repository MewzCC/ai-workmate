package com.aiworkmate.service.model;

import java.time.LocalDateTime;

public record ExpenseAgentDraftReceipt(
        long applicationId,
        String formKey,
        String status,
        int version,
        LocalDateTime createdAt
) { }
