package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AccessUserRow(
        Long id,
        String name,
        String email,
        String role,
        Integer status,
        Long tenantId,
        Long departmentId,
        Long positionId,
        Long approverUserId,
        Long permissionVersion,
        LocalDateTime updatedAt,
        String avatar
) {
}
