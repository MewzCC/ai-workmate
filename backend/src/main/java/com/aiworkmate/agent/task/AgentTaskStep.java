package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task_step")
public class AgentTaskStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer sequenceNo;
    private String toolCode;
    private String toolVersion;
    private String schemaHash;
    private String args;
    private String argsHash;
    private String riskLevel;
    private String status;
    private Integer attemptCount;
    private String result;
    private String resultSummary;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime timeoutAt;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
