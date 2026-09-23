package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ObservabilityChartPreference(
        @NotBlank String id,
        String kind,
        @Size(max = 40) String title,
        @NotBlank String mode,
        @NotNull @Size(max = 8) List<@NotNull String> content,
        @NotBlank String size,
        String granularity) {
    public ObservabilityChartPreference(String id, String mode, List<String> content, String size) {
        this(id, null, null, mode, content, size, null);
    }
}
