package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SealUseRequest(
        @NotNull(message = "{validation.version.required}") Integer version,
        @NotNull @Min(1) Integer actualCopies,
        @Size(max = 500) String remark
) {
}
