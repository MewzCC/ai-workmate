package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SupplierResponse(
        Long id,
        String code,
        String name,
        String shortName,
        String unifiedSocialCreditCode,
        String category,
        String supplierLevel,
        String status,
        String contactName,
        String contactPhone,
        String contactEmail,
        String address,
        String paymentTerms,
        String riskNote,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canManage,
        List<String> allowedTransitions
) {
}
