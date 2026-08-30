package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetOperationRequest(
        @NotNull(message = "{validation.version.required}") Integer version,
        Long targetOwnerUserId,
        Long targetDepartmentId,
        @Size(max = 500) String reason
) {
}
