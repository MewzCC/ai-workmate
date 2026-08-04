package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "{validation.roleCode.format}")
        String code,
        @NotBlank
        @Size(max = 60)
        String name,
        @NotBlank
        @Size(max = 255)
        String description
) {
}
