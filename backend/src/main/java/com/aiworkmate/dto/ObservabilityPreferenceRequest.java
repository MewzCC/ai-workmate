package com.aiworkmate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ObservabilityPreferenceRequest(
        @NotNull @Size(min = 4, max = 4) List<@NotNull @Valid ObservabilityChartPreference> charts) {
}
