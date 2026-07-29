package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record WorkflowStageResponse(
        String key,
        String title,
        String status,
        String actorName,
        LocalDateTime occurredAt,
        String description
) {
}
