package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record RuntimeLogDetailResponse(
        String source,
        Long id,
        String referenceCode,
        String operation,
        String outcome,
        String decision,
        String decisionCode,
        Integer statusCode,
        Long durationMs,
        String operatorLabel,
        String traceId,
        String requestFingerprint,
        String detailPreview,
        String errorCode,
        Boolean handlerInvoked,
        Integer resultBytes,
        Integer attempt,
        String actorType,
        String eventType,
        String clientIp,
        String userAgent,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
