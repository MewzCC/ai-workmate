package com.aiworkmate.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record AiChannelRequest(@NotBlank @Size(max=80) String name,
        @NotNull @Valid AiConfigurationRequest configuration) {
    @Override public String toString() { return "AiChannelRequest[redacted]"; }
}
