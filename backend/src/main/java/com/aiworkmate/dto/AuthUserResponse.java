package com.aiworkmate.dto;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String name,
        String email,
        String role,
        String avatarUrl,
        List<String> permissions
) {
}
