package com.aiworkmate.agent.tool.port;

/**
 * Gateway-derived identity and trace context for a domain tool call.
 *
 * <p>This context is never populated from model arguments. Adapters may transport it to a remote
 * domain service, but the receiving domain service must still resolve live permissions, tenant
 * ownership and business state before executing the operation.</p>
 */
public record ToolActorContext(
        long tenantId,
        long userId,
        long taskId,
        long stepId,
        int attempt,
        String traceId
) {
    public ToolActorContext {
        if (tenantId <= 0 || userId <= 0 || taskId <= 0 || stepId <= 0) {
            throw new IllegalArgumentException("Trusted tool identifiers must be positive");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("Tool attempt must not be negative");
        }
        if (traceId == null || traceId.isBlank() || traceId.length() > 128) {
            throw new IllegalArgumentException("Tool traceId must be between 1 and 128 characters");
        }
    }
}
