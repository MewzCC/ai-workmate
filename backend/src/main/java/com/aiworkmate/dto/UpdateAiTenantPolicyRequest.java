package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAiTenantPolicyRequest(
        @NotNull(message = "{validation.agent.enabled.required}") Boolean enabled,
        @NotNull(message = "{validation.agent.writeEnabled.required}") Boolean writeToolsEnabled
) {}
