package com.aiworkmate.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record AiTaskPlanResponse(String taskId, String status, int planVersion, String planHash,
                                 String riskLevel, boolean confirmationRequired, OffsetDateTime expiresAt,
                                 String summary, List<Step> steps) {
    public record Step(int sequence, String toolCode, String title, JsonNode arguments) { }
}
