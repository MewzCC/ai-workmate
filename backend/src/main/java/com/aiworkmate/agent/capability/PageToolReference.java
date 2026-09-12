package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.ToolCode;

public record PageToolReference(ToolCode code, PageToolAccess access) {

    public PageToolReference(String toolCode, PageToolAccess access) {
        this(ToolCode.fromCode(toolCode), access);
    }

    public PageToolReference {
        if (code == null) {
            throw new IllegalArgumentException("Page tool code is required");
        }
        if (access == null) {
            throw new IllegalArgumentException("Page tool access is required");
        }
    }

    public String toolCode() {
        return code.code();
    }
}
