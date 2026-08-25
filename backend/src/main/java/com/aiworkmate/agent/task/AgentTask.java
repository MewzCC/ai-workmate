package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long tenantId;
    private Long userId;
    private Long conversationId;
    private String pageId;
    private String input;
    private String pageContext;
    private String plan;
    private String planHash;
    private Integer planVersion;
    private String maxRiskLevel;
    private String status;
    private String confirmationTokenHash;
    private LocalDateTime confirmationExpiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime confirmationConsumedAt;
    private LocalDateTime timeoutAt;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatAt;
    private Integer attemptCount;
    private String plannerModel;
    private String promptVersion;
    private Long planningLatencyMs;
    private Integer estimatedTokens;
    private Integer toolCallCount;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
