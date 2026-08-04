package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UpdateRolePermissionsRequest(
        @NotNull(message = "{validation.permissionCodes.notNull}")
        Set<
                @Pattern(
                        regexp = "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$",
                        message = "{validation.permissionCodes.pattern}"
                )
                String
                > permissionCodes
) {
}
