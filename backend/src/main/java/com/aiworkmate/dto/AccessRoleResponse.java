package com.aiworkmate.dto;

import java.util.List;

public record AccessRoleResponse(
        String code,
        String name,
        String description,
        boolean builtin,
        List<String> permissions
) {
    public AccessRoleResponse(String code, String name, String description, boolean builtin) {
        this(code, name, description, builtin, List.of());
    }

    public AccessRoleResponse withPermissions(List<String> values) {
        return new AccessRoleResponse(code, name, description, builtin, List.copyOf(values));
    }
}
