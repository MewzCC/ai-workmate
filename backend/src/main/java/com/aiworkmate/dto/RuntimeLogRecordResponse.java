package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record RuntimeLogRecordResponse(
        String source,
        Long id,
        String referenceCode,
        String operation,
        String outcome,
        String decision,
        Integer statusCode,
        Long durationMs,
        String operatorLabel,
        String traceId,
        String errorCode,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
