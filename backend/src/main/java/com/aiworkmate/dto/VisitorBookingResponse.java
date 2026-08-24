package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 访客预约详情。
 */
public record VisitorBookingResponse(
        Long id,
        Long applicantUserId,
        String applicantName,
        Long approverUserId,
        String approverName,
        Long hostUserId,
        String hostName,
        String visitorName,
        String visitorCompany,
        String visitorPhone,
        String purpose,
        LocalDateTime expectedVisitAt,
        LocalDateTime expectedLeaveAt,
        String plateNumber,
        Integer partySize,
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
