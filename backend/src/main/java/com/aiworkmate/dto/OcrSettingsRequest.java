package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record OcrSettingsRequest(@NotNull Boolean forcePdfOcr) {
}
