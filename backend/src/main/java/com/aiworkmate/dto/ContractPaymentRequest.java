package com.aiworkmate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractPaymentRequest(
        @NotNull(message = "{validation.contract.paymentAmount.required}")
        @DecimalMin(value = "0.01", message = "{validation.contract.paymentAmount.invalid}")
        @Digits(integer = 16, fraction = 2, message = "{validation.contract.paymentAmount.invalid}") BigDecimal amount,
        @NotNull(message = "{validation.contract.paymentDate.required}") LocalDate paymentDate,
        @NotBlank(message = "{validation.contract.paymentReference.required}")
        @Size(max = 100, message = "{validation.contract.paymentReference.tooLong}") String reference,
        @Size(max = 500, message = "{validation.contract.reason.tooLong}") String note,
        @NotNull(message = "{validation.version.required}") Integer version
) {
}
