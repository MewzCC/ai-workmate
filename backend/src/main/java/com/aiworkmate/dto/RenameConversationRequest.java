package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameConversationRequest(
        @NotBlank(message = "{validation.conversationTitle.notBlank}")
        @Size(max = 100, message = "{validation.conversationTitle.maxLength}")
        String title
) {
}
