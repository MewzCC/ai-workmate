package com.aiworkmate.dto;

import jakarta.validation.constraints.Pattern;

public record MessageFeedbackRequest(
        @Pattern(regexp = "like|dislike|none", message = "{validation.feedback.invalid}") String feedback
) {
}
