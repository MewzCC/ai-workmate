package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class AiTaskPlanRequest {
    @NotBlank(message = "input is required")
    @Size(max = 4096, message = "validation.agent.input.tooLong")
    private String input;

    @NotBlank(message = "pageId is required")
    @Size(max = 80, message = "validation.agent.pageId.tooLong")
    private String pageId;

    private JsonNode pageContext;

}
