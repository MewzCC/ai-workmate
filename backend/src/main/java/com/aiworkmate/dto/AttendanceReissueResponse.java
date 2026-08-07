package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请详情。
 */
public record AttendanceReissueResponse(
        Long id,
        Long applicantUserId,
        String applicantName,
        Long approverUserId,
        String approverName,
        LocalDate clockDate,
        String clockType,
        String reason,
        String status,
        String approverComment,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canDecide,
        boolean canWithdraw
) {
}
