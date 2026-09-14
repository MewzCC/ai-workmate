package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record BindDataPermissionPolicyRequest(@NotNull(message = "{validation.data_permission.policy.required}") Long policyId) {
}
