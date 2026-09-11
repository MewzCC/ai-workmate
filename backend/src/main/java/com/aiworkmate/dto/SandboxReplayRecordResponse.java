package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record SandboxReplayRecordResponse(
        Long id,
        Long sourceInvocationId,
        String endpointCode,
        String endpointName,
        String method,
        String relativePath,
        String baselineOutcome,
        Integer baselineHttpStatus,
        String status,
        Integer replayHttpStatus,
        Long replayDurationMs,
        String comparisonResult,
        String requestedByLabel,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
