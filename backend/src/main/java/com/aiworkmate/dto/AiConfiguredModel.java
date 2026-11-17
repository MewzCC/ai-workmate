package com.aiworkmate.dto;

import jakarta.validation.constraints.*;
public record AiConfiguredModel(
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", message="{validation.chat.model.invalid}") String id,
        @Min(1024) @Max(2097152) int contextWindow,
        @Min(16) @Max(393216) int maxOutputTokens,
        boolean supportsImages) {
    public static AiConfiguredModel defaults(String id) { return new AiConfiguredModel(id, 32768, 4096, false); }
}
