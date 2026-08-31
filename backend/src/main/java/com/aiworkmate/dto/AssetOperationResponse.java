package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AssetOperationResponse(
        Long id,
        String operationType,
        String fromStatus,
        String toStatus,
        Long fromDepartmentId,
        String fromDepartmentName,
        Long toDepartmentId,
        String toDepartmentName,
        Long fromOwnerUserId,
        String fromOwnerName,
        Long toOwnerUserId,
        String toOwnerName,
        Long operatorUserId,
        String operatorName,
        String reason,
        String inventoryResult,
        String actualStatus,
        Long actualDepartmentId,
        String actualDepartmentName,
        Long actualOwnerUserId,
        String actualOwnerName,
        LocalDateTime createdAt
) {
}
