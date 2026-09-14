package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContractResponse(
        Long id,
        String code,
        String name,
        String contractType,
        String counterpartyName,
        Long supplierId,
        String supplierLabel,
        Long ownerUserId,
        String ownerLabel,
        BigDecimal amount,
        BigDecimal paidAmount,
        String currency,
        LocalDate signedDate,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String fulfillmentStatus,
        String expiryState,
        long daysUntilExpiry,
        String summary,
        Integer reminderCount,
        LocalDateTime lastRemindedAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canManage,
        List<String> allowedTransitions,
        List<String> allowedFulfillmentStatuses,
        boolean canRecordPayment,
        boolean canRemind
) {
}
