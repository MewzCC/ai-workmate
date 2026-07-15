package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        String title,
        String model,
        LocalDateTime updatedAt
) {
}
