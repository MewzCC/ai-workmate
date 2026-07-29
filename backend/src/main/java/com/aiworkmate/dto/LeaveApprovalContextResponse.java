package com.aiworkmate.dto;

public record LeaveApprovalContextResponse(
        String applicantName,
        String departmentName,
        String positionName,
        Long approverUserId,
        String approverName,
        String approverSource,
        boolean approverConfigured,
        long approvalDueHours
) {
}
