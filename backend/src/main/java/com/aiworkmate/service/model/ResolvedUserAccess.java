package com.aiworkmate.service.model;

import java.util.List;

public record ResolvedUserAccess(
        Long userId,
        String username,
        Long tenantId,
        String role,
        List<String> roles,
        List<String> permissions,
        List<String> dataScopes,
        Long permissionVersion
) {
    public ResolvedUserAccess(Long userId,
                              String username,
                              String role,
                              List<String> permissions) {
        this(userId, username, 1L, role, List.of(role), permissions, List.of("SELF"), 1L);
    }
}
