package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantProfileRequest(
        @NotBlank @Size(max = 120) String tenantName,
        @Size(max = 40) String tenantShortName,
        @NotBlank @Pattern(regexp = "zh-CN|en-US", message = "{validation.tenant.locale.invalid}") String locale,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_+-]+(?:/[A-Za-z0-9_+-]+)*", message = "{validation.tenant.timezone.invalid}") String timezone,
        @NotNull Integer version
) {
}
