package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 印章用印详情。
 */
public record SealUsageResponse(
        Long id,
        Long applicantUserId,
        String applicantName,
        Long approverUserId,
        String approverName,
        String sealType,
        String documentTitle,
        String usageReason,
        Integer copies,
        String status,
        Integer version,
        Long workflowInstanceId,
        Long taskId,
        Integer taskVersion,
        String taskStatus,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canWithdraw,
        boolean canDecide
) {
}
