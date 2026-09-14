package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePageActionPolicyRequest(
        @NotNull(message = "{validation.pageAction.enabled.required}") Boolean enabled,
        @NotNull(message = "{validation.pageAction.version.required}") Integer version,
        @NotBlank(message = "{validation.pageAction.reason.required}")
        @Size(min = 2, max = 300, message = "{validation.pageAction.reason.size}") String reason
) {}
