package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAiToolStatusRequest(
        @NotNull(message = "{validation.agent.toolEnabled.required}") Boolean enabled
) {}
