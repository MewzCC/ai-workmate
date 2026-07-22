package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 100, message = "会话标题不能超过 100 个字符")
        String title
) {
}
