package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tenant_policy")
public class AgentTenantPolicy {
    @TableId
    private Long tenantId;
    private Boolean enabled;
    private Boolean writeToolsEnabled;
    private Integer maxPlanSteps;
    private Integer maxToolCalls;
    private Integer maxConcurrentTasksPerUser;
    private Integer maxQuerySize;
    private Integer maxToolTimeoutMs;
    private Integer maxTaskTimeoutMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
