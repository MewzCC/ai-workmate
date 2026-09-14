package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DictionaryTypeRequest(
        @NotBlank(message = "{validation.dictionary.code.required}")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "{validation.dictionary.code.invalid}") String code,
        @NotBlank(message = "{validation.dictionary.name.required}")
        @Size(max = 120, message = "{validation.dictionary.name.tooLong}") String name,
        @Size(max = 500, message = "{validation.dictionary.description.tooLong}") String description,
        @Min(value = 0, message = "{validation.dictionary.sort.invalid}")
        @Max(value = 9999, message = "{validation.dictionary.sort.invalid}") Integer sortOrder,
        Integer version
) {
}
