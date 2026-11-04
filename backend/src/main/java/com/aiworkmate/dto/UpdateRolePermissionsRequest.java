package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRolePermissionsRequest(
        @NotNull(message = "{validation.permissionCodes.notNull}")
        Set<
                @NotBlank(message = "{validation.permissionCode.required}")
                @Pattern(
                        regexp = "^[a-z][a-z0-9-]*(?::[a-z][A-Za-z0-9-]*(?:\\.[a-z][A-Za-z0-9-]*)*)+$",
                        message = "{validation.permissionCodes.pattern}"
                )
                @Size(max = 80, message = "{validation.permissionCode.length}") String
                > permissionCodes
) {
}
