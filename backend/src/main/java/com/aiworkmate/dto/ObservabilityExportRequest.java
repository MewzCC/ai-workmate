package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ObservabilityExportRequest(
        @NotBlank @Pattern(regexp = "24h|7d|30d") String range,
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,64}") String chartId) {
}
