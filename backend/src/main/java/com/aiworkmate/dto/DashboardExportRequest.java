package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DashboardExportRequest(
        @NotNull(message = "validation.dashboard.export.from.required") LocalDate from,
        @NotNull(message = "validation.dashboard.export.to.required") LocalDate to,
        @Size(max = 100, message = "validation.dashboard.export.keyword.size") String keyword
) {}
