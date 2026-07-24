package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符")
        String name
) {
}
