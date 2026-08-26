package com.aiworkmate.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record AgentTaskDetailResponse(
        String taskId,
        String pageId,
        String input,
        JsonNode pageContext,
        JsonNode plan,
        String planHash,
        Integer planVersion,
        String riskLevel,
        String status,
        List<Step> steps,
        LocalDateTime timeoutAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt,
        String errorCode
) {
    public record Step(
            Integer sequence,
            String toolCode,
            String riskLevel,
            String status,
            JsonNode arguments,
            JsonNode result,
            String resultSummary,
            String errorCode,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) { }
}
