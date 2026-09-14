package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DictionaryItemRequest(
        @NotBlank(message = "{validation.dictionary.value.required}")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$", message = "{validation.dictionary.value.invalid}") String value,
        @NotBlank(message = "{validation.dictionary.label.required}")
        @Size(max = 160, message = "{validation.dictionary.label.tooLong}") String label,
        @Size(max = 500, message = "{validation.dictionary.description.tooLong}") String description,
        @Min(value = 0, message = "{validation.dictionary.sort.invalid}")
        @Max(value = 9999, message = "{validation.dictionary.sort.invalid}") Integer sortOrder,
        Integer version
) {
}
