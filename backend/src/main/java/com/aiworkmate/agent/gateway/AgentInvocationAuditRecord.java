package com.aiworkmate.agent.gateway;

import java.time.LocalDateTime;

public record AgentInvocationAuditRecord(
        String decisionId,
        String toolCode,
        String toolVersion,
        String decision,
        String decisionCode,
        boolean handlerInvoked,
        String outcome,
        Integer resultBytes,
        String errorClass,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs
) { }
