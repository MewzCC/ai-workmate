package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record SandboxReplayDetailResponse(
        Long id,
        Long sourceInvocationId,
        String endpointCode,
        String endpointName,
        String method,
        String relativePath,
        String baselineRequestFingerprint,
        String baselineOutcome,
        Integer baselineHttpStatus,
        String baselineResponsePreview,
        String status,
        Integer replayHttpStatus,
        Long replayDurationMs,
        String replayResponsePreview,
        String replayErrorCode,
        String comparisonResult,
        String traceId,
        String reason,
        String requestedByLabel,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
