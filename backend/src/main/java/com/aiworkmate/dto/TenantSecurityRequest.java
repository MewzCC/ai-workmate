package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TenantSecurityRequest(
        @NotNull @Min(8) @Max(32) Integer passwordMinLength,
        @NotNull @Min(15) @Max(1440) Integer sessionTimeoutMinutes,
        @NotNull Integer version
) {
}
