package com.aiworkmate.agent.tool.internal;

public record TrustedToolContext(
        long tenantId,
        long userId,
        long taskId,
        long stepId,
        int attempt,
        String traceId
) {
}
