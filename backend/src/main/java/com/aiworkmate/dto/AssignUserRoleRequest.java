package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssignUserRoleRequest(
        @NotBlank(message = "{validation.roleCode.required}")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "{validation.roleCode.pattern}")
        String roleCode
) {
}
