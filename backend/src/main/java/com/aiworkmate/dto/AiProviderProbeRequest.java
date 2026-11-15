package com.aiworkmate.dto;
import jakarta.validation.constraints.*;
public record AiProviderProbeRequest(@NotBlank @Size(max=512) String baseUrl,
        @Size(max=4096) String apiKey,
        @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", message="{validation.chat.model.invalid}") String model) {
    @Override public String toString() { return "AiProviderProbeRequest[redacted]"; }
}
