package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record SandboxReplayBaselineResponse(
        Long invocationId,
        Long endpointId,
        String endpointCode,
        String endpointName,
        String method,
        String relativePath,
        String outcome,
        Integer httpStatus,
        Long durationMs,
        String operatorLabel,
        LocalDateTime createdAt
) {
}
