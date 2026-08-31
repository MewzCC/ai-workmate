package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetMaintenanceRequest(
        @NotNull(message = "{validation.version.required}") Integer version,
        @NotBlank(message = "{validation.asset.operation.reason.required}")
        @Size(max = 500) String reason
) {
}
