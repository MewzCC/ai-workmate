package com.aiworkmate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WorkbenchRecordRequest(
        @NotBlank(message = "{validation.workbench.code.required}")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]{1,63}$", message = "{validation.workbench.code.invalid}")
        String code,
        @NotBlank(message = "{validation.workbench.title.required}")
        @Size(max = 160, message = "{validation.workbench.title.tooLong}")
        String title,
        @Size(max = 80, message = "{validation.workbench.category.tooLong}")
        String category,
        @Pattern(regexp = "DRAFT|ACTIVE|PENDING|COMPLETED|DISABLED|FAILED", message = "{validation.workbench.status.invalid}")
        String status,
        @DecimalMin(value = "0", message = "{validation.workbench.amount.invalid}")
        BigDecimal amount,
        @Size(max = 100, message = "{validation.workbench.owner.tooLong}")
        String owner,
        @Size(max = 4000, message = "{validation.workbench.details.tooLong}")
        String details,
        Integer version
) {
}
