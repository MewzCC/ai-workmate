package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ObservabilityChartPreference(
        @NotBlank String id,
        @NotBlank String mode,
        @NotNull @Size(max = 8) List<@NotNull String> content,
        @NotBlank String size) {
}
