package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UpdateRolePermissionsRequest(
        @NotNull(message = "权限集合不能为空")
        Set<
                @Pattern(
                        regexp = "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$",
                        message = "权限编码格式不正确"
                )
                String
                > permissionCodes
) {
}
