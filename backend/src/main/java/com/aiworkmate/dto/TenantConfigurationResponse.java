package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record TenantConfigurationResponse(
        Long id,
        String tenantName,
        String tenantShortName,
        String locale,
        String timezone,
        Integer fiscalYearStartMonth,
        Boolean approvalEnabled,
        Boolean attendanceEnabled,
        Boolean assetEnabled,
        Boolean meetingEnabled,
        Boolean visitorEnabled,
        Boolean sealEnabled,
        Integer defaultApprovalDays,
        String expenseCurrency,
        Integer passwordMinLength,
        Integer sessionTimeoutMinutes,
        Integer version,
        LocalDateTime updatedAt,
        boolean canManage
) {
}
