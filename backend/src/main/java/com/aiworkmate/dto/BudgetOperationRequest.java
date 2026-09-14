package com.aiworkmate.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetOperationRequest(
        @NotBlank @Pattern(regexp="OCCUPY|RELEASE|SPEND") String type,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal amount,
        @Size(max=100) String referenceCode,
        @Size(max=500) String note,
        @NotNull Integer version) {}
