package com.aiworkmate.agent.capability;

/**
 * Declares how a page may expose a tool. The actual side effect remains
 * authoritative in the code-owned ToolDefinition and is cross-checked later.
 */
public enum PageToolAccess {
    READ,
    SINGLE_WRITE
}
