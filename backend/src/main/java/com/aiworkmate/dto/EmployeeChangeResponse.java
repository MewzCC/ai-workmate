package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeChangeResponse(
        Long id,
        Long employeeUserId,
        String employeeName,
        String employeeEmail,
        Long applicantUserId,
        String applicantName,
        Long reviewApproverUserId,
        String reviewApproverName,
        String changeType,
        LocalDate effectiveDate,
        Long currentDepartmentId,
        String currentDepartmentName,
        Long currentPositionId,
        String currentPositionName,
        Long currentSupervisorUserId,
        String currentSupervisorName,
        Long targetDepartmentId,
        String targetDepartmentName,
        Long targetPositionId,
        String targetPositionName,
        Long targetSupervisorUserId,
        String targetSupervisorName,
        String reason,
        String status,
        String decisionComment,
        Integer version,
        boolean canApprove,
        boolean canWithdraw,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime appliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
