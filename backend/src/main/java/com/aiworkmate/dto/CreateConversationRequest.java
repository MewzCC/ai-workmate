package com.aiworkmate.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 100, message = "{validation.conversationTitle.maxLength}") String title,
        @Size(max = 80, message = "{validation.modelName.maxLength}") String model
) {
}
