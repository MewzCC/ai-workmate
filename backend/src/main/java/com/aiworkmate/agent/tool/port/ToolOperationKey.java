package com.aiworkmate.agent.tool.port;

/**
 * Stable write-operation identity transported across an Agent domain boundary.
 *
 * <p>The value is generated from trusted task coordinates. It supports domain idempotency and
 * result verification, but is never an authorization or replay permission.</p>
 */
public record ToolOperationKey(String value) {
    public ToolOperationKey {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Tool operation key must be between 1 and 128 characters");
        }
    }
}
