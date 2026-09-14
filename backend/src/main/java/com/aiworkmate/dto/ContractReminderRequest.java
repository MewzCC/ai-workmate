package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record ContractReminderRequest(
        @NotNull(message = "{validation.version.required}") Integer version
) {
}
