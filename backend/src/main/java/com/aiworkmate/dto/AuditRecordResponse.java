package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AuditRecordResponse(
        Long id,
        Long actorUserId,
        String actorName,
        String resourceType,
        String resourceId,
        String action,
        String result,
        String summary,
        String traceId,
        LocalDateTime createdAt
) {
}
