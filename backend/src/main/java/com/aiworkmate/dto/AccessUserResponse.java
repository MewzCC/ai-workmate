package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AccessUserResponse(
        Long id,
        String name,
        String email,
        String role,
        Integer status,
        LocalDateTime updatedAt
) {
}
