package com.aiworkmate.dto;

import jakarta.validation.constraints.Pattern;

public record MessageFeedbackRequest(
        @Pattern(regexp = "like|dislike|none", message = "反馈类型不合法") String feedback
) {
}
