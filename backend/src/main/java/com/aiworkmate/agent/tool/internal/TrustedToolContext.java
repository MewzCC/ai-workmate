package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ToolActorContext;

public record TrustedToolContext(
        long tenantId,
        long userId,
        long taskId,
        long stepId,
        int attempt,
        String traceId
) {
    public ToolActorContext actor() {
        return new ToolActorContext(tenantId, userId, taskId, stepId, attempt, traceId);
    }
}
