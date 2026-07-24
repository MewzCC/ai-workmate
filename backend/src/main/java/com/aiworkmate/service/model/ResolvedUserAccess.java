package com.aiworkmate.service.model;

import java.util.List;

public record ResolvedUserAccess(
        Long userId,
        String username,
        String role,
        List<String> permissions
) {
}
