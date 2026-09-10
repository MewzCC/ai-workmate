package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TenantBusinessRequest(
        @NotNull @Min(1) @Max(12) Integer fiscalYearStartMonth,
        @NotNull @Min(1) @Max(30) Integer defaultApprovalDays,
        @NotBlank @Pattern(regexp = "CNY|USD|EUR|HKD", message = "{validation.tenant.currency.invalid}") String expenseCurrency,
        @NotNull Integer version
) {
}
