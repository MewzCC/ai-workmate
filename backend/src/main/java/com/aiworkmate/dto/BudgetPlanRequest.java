package com.aiworkmate.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetPlanRequest(
        @NotBlank(message="{validation.budget.code.required}")
        @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$", message="{validation.budget.code.invalid}") String code,
        @NotBlank(message="{validation.budget.name.required}") @Size(max=160) String name,
        @NotNull @Min(2000) @Max(2200) Integer fiscalYear,
        @NotNull(message="{validation.budget.owner.required}") Long ownerUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal totalAmount,
        @NotBlank @Pattern(regexp="CNY|USD|EUR|HKD") String currency,
        @NotNull @Min(1) @Max(100) Integer warningThreshold,
        @Size(max=2000) String summary,
        Integer version) {}
