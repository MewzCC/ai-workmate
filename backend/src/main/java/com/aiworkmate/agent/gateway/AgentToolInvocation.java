package com.aiworkmate.agent.gateway;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_invocation")
public class AgentToolInvocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String decisionId;
    private Long tenantId;
    private Long userId;
    private Long taskId;
    private Long stepId;
    private Integer attempt;
    private String toolCode;
    private String toolVersion;
    private String decision;
    private String decisionCode;
    private String argsHash;
    private String argsSummary;
    private Boolean handlerInvoked;
    private String outcome;
    private Integer resultBytes;
    private String errorClass;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
}
