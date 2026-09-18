package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.ToolCode;

public record PageToolReference(ToolCode code) {

    public PageToolReference(String toolCode) {
        this(ToolCode.fromCode(toolCode));
    }

    public PageToolReference {
        if (code == null) {
            throw new IllegalArgumentException("Page tool code is required");
        }
    }

    public String toolCode() {
        return code.code();
    }

    public PageToolAccess access() {
        return code.isWrite() ? PageToolAccess.SINGLE_WRITE : PageToolAccess.READ;
    }
}
