package com.aiworkmate.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 100, message = "会话标题不能超过 100 个字符") String title,
        @Size(max = 80, message = "模型名称不能超过 80 个字符") String model
) {
}
