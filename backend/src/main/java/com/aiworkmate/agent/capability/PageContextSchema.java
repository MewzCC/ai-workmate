package com.aiworkmate.agent.capability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record PageContextSchema(int maxBytes, int maxDepth, List<PageContextField> fields) {
    public static final int PLATFORM_MAX_BYTES = 16 * 1024;
    public static final int PLATFORM_MAX_DEPTH = 3;

    public PageContextSchema {
        if (maxBytes < 1 || maxBytes > PLATFORM_MAX_BYTES) {
            throw new IllegalArgumentException("Page context maxBytes exceeds platform limit");
        }
        if (maxDepth < 1 || maxDepth > PLATFORM_MAX_DEPTH) {
            throw new IllegalArgumentException("Page context maxDepth exceeds platform limit");
        }
        fields = List.copyOf(fields == null ? List.of() : fields);
        Set<String> names = new HashSet<>();
        for (PageContextField field : fields) {
            if (field == null || !names.add(field.name())) {
                throw new IllegalArgumentException("Duplicate or null page context field");
            }
        }
    }

    public static PageContextSchema empty() {
        return new PageContextSchema(1024, 1, List.of());
    }
}
