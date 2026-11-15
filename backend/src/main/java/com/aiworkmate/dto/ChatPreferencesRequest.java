package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChatPreferencesRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}",
                message = "{validation.chat.model.invalid}") String model,
        @NotNull @Min(1) @Max(20) Integer maxContextRounds,
        @NotNull Boolean stream,
        @NotNull Boolean forcePdfOcr
) {
}
