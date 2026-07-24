package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "角色编码须为 3-40 位大写字母、数字或下划线")
        String code,
        @NotBlank
        @Size(max = 60)
        String name,
        @NotBlank
        @Size(max = 255)
        String description
) {
}
