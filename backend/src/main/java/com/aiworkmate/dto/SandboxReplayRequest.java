package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SandboxReplayRequest(
        @NotNull(message = "{validation.sandboxReplay.source.required}")
        Long sourceInvocationId,
        @NotBlank(message = "{validation.sandboxReplay.reason.required}")
        @Size(min = 5, max = 500, message = "{validation.sandboxReplay.reason.size}")
        String reason,
        @NotBlank(message = "{validation.sandboxReplay.idempotency.required}")
        @Pattern(regexp = "^[A-Za-z0-9-]{16,64}$", message = "{validation.sandboxReplay.idempotency.invalid}")
        String idempotencyKey
) {
}
