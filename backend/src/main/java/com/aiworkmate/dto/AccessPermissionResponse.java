package com.aiworkmate.dto;

public record AccessPermissionResponse(
        String code,
        String name,
        String module,
        String description
) {
}
