package com.aiworkmate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskPlanResponse {
    private String taskId;
    private String type;
    private String riskLevel;
    private boolean requireConfirm;
    private String summary;
    private List<Step> steps;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private String title;
        private String description;
    }
}
