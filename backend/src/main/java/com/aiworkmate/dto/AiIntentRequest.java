package com.aiworkmate.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiIntentRequest(
        @NotBlank(message = "{validation.agent.input.required}")
        @Size(max = 4096, message = "{validation.agent.input.tooLong}") String input) {
    @JsonAnySetter
    public void rejectUnknown(String key, JsonNode value) {
        throw new IllegalArgumentException("Unknown intent field");
    }
}
