package com.aiworkmate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskExecuteResponse {
    private boolean success;
    private String auditId;
    private String message;
    private ExecuteResult result;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecuteResult {
        private int successCount;
        private int pendingConfirmCount;
        private int rejectSuggestCount;
    }
}
