package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "{validation.name.notBlank}")
        @Size(max = 50, message = "{validation.name.maxLength}")
        String name
) {
}
