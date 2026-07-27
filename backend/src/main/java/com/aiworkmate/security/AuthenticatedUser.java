package com.aiworkmate.security;

import java.util.List;

public record AuthenticatedUser(
        Long userId,
        String username,
        Long tenantId,
        String role,
        List<String> roles,
        List<String> permissions,
        List<String> dataScopes,
        Long permissionVersion
) {
}
