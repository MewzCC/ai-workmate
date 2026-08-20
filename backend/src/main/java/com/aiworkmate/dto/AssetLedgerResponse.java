package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产台账详情。
 */
public record AssetLedgerResponse(
        Long id,
        String assetCode,
        String name,
        String category,
        String specification,
        String status,
        Long departmentId,
        String departmentName,
        Long ownerUserId,
        String ownerName,
        LocalDate purchaseDate,
        BigDecimal originalValue,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}
