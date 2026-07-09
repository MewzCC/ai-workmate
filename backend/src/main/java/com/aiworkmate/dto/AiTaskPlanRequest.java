package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTaskPlanRequest {
    @NotBlank(message = "input is required")
    private String input;

    @NotBlank(message = "pageId is required")
    private String pageId;

    @NotBlank(message = "role is required")
    private String role;
}
