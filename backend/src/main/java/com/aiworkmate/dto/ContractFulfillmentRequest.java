package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContractFulfillmentRequest(
        @NotBlank(message = "{validation.contract.fulfillment.required}")
        @Pattern(regexp = "^(IN_PROGRESS|FULFILLED|BREACHED)$",
                message = "{validation.contract.fulfillment.invalid}") String status,
        @Size(max = 500, message = "{validation.contract.reason.tooLong}") String reason,
        @NotNull(message = "{validation.version.required}") Integer version
) {
}
