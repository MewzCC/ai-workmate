package com.aiworkmate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractRequest(
        @NotBlank(message = "{validation.contract.code.required}")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$", message = "{validation.contract.code.invalid}")
        String code,
        @NotBlank(message = "{validation.contract.name.required}")
        @Size(max = 160, message = "{validation.contract.name.tooLong}") String name,
        @NotBlank(message = "{validation.contract.type.required}")
        @Pattern(regexp = "^(PURCHASE|SALES|SERVICE|LEASE|OTHER)$",
                message = "{validation.contract.type.invalid}") String contractType,
        @NotBlank(message = "{validation.contract.counterparty.required}")
        @Size(max = 160, message = "{validation.contract.counterparty.tooLong}") String counterpartyName,
        Long supplierId,
        @NotNull(message = "{validation.contract.owner.required}") Long ownerUserId,
        @NotNull(message = "{validation.contract.amount.required}")
        @DecimalMin(value = "0.01", message = "{validation.contract.amount.invalid}")
        @Digits(integer = 16, fraction = 2, message = "{validation.contract.amount.invalid}") BigDecimal amount,
        @NotBlank(message = "{validation.contract.currency.required}")
        @Pattern(regexp = "^(CNY|USD|EUR|HKD)$", message = "{validation.contract.currency.invalid}") String currency,
        LocalDate signedDate,
        @NotNull(message = "{validation.contract.startDate.required}") LocalDate startDate,
        @NotNull(message = "{validation.contract.endDate.required}") LocalDate endDate,
        @Size(max = 2000, message = "{validation.contract.summary.tooLong}") String summary,
        Integer version
) {
}
