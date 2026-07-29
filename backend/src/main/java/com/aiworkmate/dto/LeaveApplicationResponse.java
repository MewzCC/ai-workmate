package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        String taskStatus,
        LocalDateTime taskDueAt,
        boolean overdue,
        String workflowStatus,
        String currentStage,
        List<WorkflowStageResponse> workflowStages,
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
