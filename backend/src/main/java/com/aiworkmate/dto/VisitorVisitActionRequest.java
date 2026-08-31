package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VisitorVisitActionRequest(
        @NotNull(message = "{validation.version.required}") Integer version,
        @Size(max = 500) String remark
) {
}
