package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AccessUserResponse(
        Long id,
        String name,
        String email,
        String role,
        List<String> roles,
        Integer status,
        Long departmentId,
        Long positionId,
        Long approverUserId,
        Long permissionVersion,
        LocalDateTime updatedAt,
        String avatarUrl
) {
}
