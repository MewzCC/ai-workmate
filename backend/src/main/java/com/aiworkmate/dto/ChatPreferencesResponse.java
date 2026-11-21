package com.aiworkmate.dto;

public record ChatPreferencesResponse(
        String model,
        int maxContextRounds,
        boolean stream,
        boolean forcePdfOcr,
        boolean initialized,
        String ocrModelRef
) {
    public ChatPreferencesResponse(String model, int maxContextRounds, boolean stream,
                                   boolean forcePdfOcr, boolean initialized) {
        this(model, maxContextRounds, stream, forcePdfOcr, initialized, "default");
    }
}
