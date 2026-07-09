package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTaskExecuteRequest {
    @NotBlank(message = "taskId is required")
    private String taskId;

    @NotBlank(message = "type is required")
    private String type;

    private boolean confirm;
}
