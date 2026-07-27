package com.aiworkmate.dto;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String name,
        String email,
        Long tenantId,
        String role,
        List<String> roles,
        String avatarUrl,
        List<String> permissions,
        List<String> dataScopes,
        Long permissionVersion
) {
    public AuthUserResponse(Long id,
                            String name,
                            String email,
                            String role,
                            String avatarUrl,
                            List<String> permissions) {
        this(id, name, email, 1L, role, List.of(role), avatarUrl,
                permissions, List.of("SELF"), 1L);
    }
}
