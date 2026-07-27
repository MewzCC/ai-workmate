package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveApplicationResponse(
        Long id,
        Long applicantUserId,
        String applicantName,
        Long approverUserId,
        String approverName,
        String leaveType,
        LocalDate startDate,
        String startPeriod,
        LocalDate endDate,
        String endPeriod,
        int durationHalfDays,
        double durationDays,
        String reason,
        String status,
        int version,
        Long taskId,
        Integer taskVersion,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canSubmit,
        boolean canWithdraw,
        boolean canApprove
) {
}
