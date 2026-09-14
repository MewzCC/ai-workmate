package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateAiRoleToolsRequest(
        @NotNull(message = "{validation.agent.toolCodes.required}") Set<String> toolCodes
) {}
