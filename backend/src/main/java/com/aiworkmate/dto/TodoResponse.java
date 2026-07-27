package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record TodoResponse(
        Long id,
        Long applicationId,
        Long applicantUserId,
        String applicantName,
        String leaveType,
        int durationHalfDays,
        String status,
        int version,
        LocalDateTime submittedAt,
        LocalDateTime dueAt,
        boolean overdue
) {
}
