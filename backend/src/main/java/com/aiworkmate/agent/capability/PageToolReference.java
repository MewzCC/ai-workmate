package com.aiworkmate.agent.capability;

import java.util.regex.Pattern;

public record PageToolReference(String toolCode, PageToolAccess access) {
    private static final Pattern TOOL_CODE_PATTERN =
            Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");

    public PageToolReference {
        if (toolCode == null || !TOOL_CODE_PATTERN.matcher(toolCode).matches()) {
            throw new IllegalArgumentException("Invalid page tool code");
        }
        if (access == null) {
            throw new IllegalArgumentException("Page tool access is required");
        }
    }
}
