package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DictionaryStatusRequest(
        @NotNull @Pattern(regexp = "ACTIVE|DISABLED", message = "{validation.dictionary.status.invalid}") String status,
        @NotNull @Min(0) Integer version
) {
}
