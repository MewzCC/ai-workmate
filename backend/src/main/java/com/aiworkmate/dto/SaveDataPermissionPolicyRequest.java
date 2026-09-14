package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record SaveDataPermissionPolicyRequest(
        @NotBlank(message = "{validation.data_permission.name.required}") @Size(max = 80, message = "{validation.data_permission.name.size}") String name,
        @Size(max = 255, message = "{validation.data_permission.description.size}") String description,
        @NotBlank(message = "{validation.data_permission.scope.required}") @Pattern(regexp = "^(SELF|DEPARTMENT|DEPARTMENT_AND_CHILDREN|CUSTOM_DEPARTMENTS|ALL)$", message = "{validation.data_permission.scope.invalid}") String scopeType,
        @NotNull(message = "{validation.data_permission.departments.required}") Set<Long> departmentIds,
        @NotNull(message = "{validation.data_permission.enabled.required}") Boolean enabled,
        Long version) {
}
