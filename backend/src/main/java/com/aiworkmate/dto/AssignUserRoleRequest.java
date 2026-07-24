package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssignUserRoleRequest(
        @NotBlank(message = "请选择角色")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "角色编码格式不正确")
        String roleCode
) {
}
