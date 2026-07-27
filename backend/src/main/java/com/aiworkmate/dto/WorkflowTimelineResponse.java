package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record WorkflowTimelineResponse(
        Long id,
        Long actorUserId,
        String actorName,
        String action,
        String fromStatus,
        String toStatus,
        String comment,
        LocalDateTime createdAt
) {
}
