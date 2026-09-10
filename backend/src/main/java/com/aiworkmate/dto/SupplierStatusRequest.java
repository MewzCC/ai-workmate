package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierStatusRequest(
        @NotBlank(message = "{validation.supplier.status.required}")
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|BLACKLISTED)$", message = "{validation.supplier.status.invalid}")
        String status,
        @Size(max = 500, message = "{validation.supplier.statusReason.tooLong}") String reason,
        @NotNull(message = "{validation.version.required}") Integer version
) {
}
