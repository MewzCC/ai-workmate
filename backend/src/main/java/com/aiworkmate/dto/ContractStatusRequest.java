package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContractStatusRequest(
        @NotBlank(message = "{validation.contract.status.required}")
        @Pattern(regexp = "^(ACTIVE|COMPLETED|TERMINATED)$", message = "{validation.contract.status.invalid}")
        String status,
        @Size(max = 500, message = "{validation.contract.reason.tooLong}") String reason,
        @NotNull(message = "{validation.version.required}") Integer version
) {
}
