package com.aiworkmate.dto;

public record LeaveApprovalContextRow(
        String applicantName,
        String departmentName,
        String positionName,
        String approverName,
        String applicantAvatar,
        java.time.LocalDateTime applicantUpdatedAt
) {
}
