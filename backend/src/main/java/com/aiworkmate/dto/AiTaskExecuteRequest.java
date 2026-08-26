package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiTaskExecuteRequest(
        @NotNull(message = "validation.agent.planVersion.required")
        @Min(value = 1, message = "validation.agent.planVersion.invalid") Integer planVersion,
        @NotBlank(message = "validation.agent.planHash.required")
        @Pattern(regexp = "^sha256:[0-9a-f]{64}$", message = "validation.agent.planHash.invalid") String planHash,
        @Size(max = 128, message = "validation.agent.confirmationToken.invalid") String confirmationToken) { }
