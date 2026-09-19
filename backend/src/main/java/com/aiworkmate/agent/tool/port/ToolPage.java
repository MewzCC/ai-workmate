package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Shared immutable page envelope for typed Agent domain ports. */
public record ToolPage<T>(List<T> items, long total, int page, int size) {
    public ToolPage {
        items = List.copyOf(items);
    }
}
