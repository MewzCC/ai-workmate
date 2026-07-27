package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VersionRequest(
        @NotNull @Min(0) Integer version
) {
}
