package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeChangeDecisionRequest(
        @NotNull Integer version,
        @Size(max = 1000) String comment
) {
}
