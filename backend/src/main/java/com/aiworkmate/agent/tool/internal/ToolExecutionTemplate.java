package com.aiworkmate.agent.tool.internal;

/**
 * Code-owned execution shapes for tool handlers. The handler only declares how
 * it executes; risk and confirmation policy remain owned by ToolDefinition.
 */
public enum ToolExecutionTemplate {
    READ_ONLY(false, false),
    DIRECT_WRITE(true, false),
    VERSIONED_WRITE(true, false),
    OPERATION_KEY_WRITE(true, true),
    NATURALLY_IDEMPOTENT_WRITE(true, true);

    private final boolean write;
    private final boolean businessRetrySafe;

    ToolExecutionTemplate(boolean write, boolean businessRetrySafe) {
        this.write = write;
        this.businessRetrySafe = businessRetrySafe;
    }

    public boolean isWrite() {
        return write;
    }

    public boolean isBusinessRetrySafe() {
        return businessRetrySafe;
    }
}
