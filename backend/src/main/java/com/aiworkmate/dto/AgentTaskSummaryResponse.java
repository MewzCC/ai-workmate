package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AgentTaskSummaryResponse(
        String taskId,
        String pageId,
        String status,
        String riskLevel,
        Integer planVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt,
        String errorCode
) { }
