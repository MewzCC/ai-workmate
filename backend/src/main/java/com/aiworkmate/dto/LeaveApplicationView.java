package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveApplicationView(
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
        Integer durationHalfDays,
        String reason,
        String status,
        Integer version,
        Long taskId,
        Integer taskVersion,
        String taskStatus,
        LocalDateTime taskDueAt,
        String workflowStatus,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
