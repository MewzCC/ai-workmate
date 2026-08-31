package com.aiworkmate.dto;

public record ChatPreferencesResponse(
        String model,
        int maxContextRounds,
        boolean stream,
        boolean forcePdfOcr,
        boolean initialized
) {
}
