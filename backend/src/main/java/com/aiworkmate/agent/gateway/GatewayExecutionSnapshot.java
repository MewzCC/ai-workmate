package com.aiworkmate.agent.gateway;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GatewayExecutionSnapshot {
    private Long taskId;
    private Long stepId;
    private Integer sequenceNo;
    private Long tenantId;
    private Long userId;
    private String taskStatus;
    private String stepStatus;
    private String workerId;
    private String leaseTokenHash;
    private LocalDateTime leaseUntil;
    private LocalDateTime taskTimeoutAt;
    private LocalDateTime stepTimeoutAt;
    private Integer taskAttempt;
    private Integer stepAttempt;
    private String plan;
    private String planHash;
    private Integer planVersion;
    private String taskRiskLevel;
    private LocalDateTime confirmationConsumedAt;
    private Integer toolCallCount;
    private String toolCode;
    private String toolVersion;
    private String schemaHash;
    private String arguments;
    private String argsHash;
    private String stepRiskLevel;
    private String traceId;
}
