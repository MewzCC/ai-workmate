package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool")
public class AgentTool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String description;
    private String handlerVersion;
    private String parametersSchema;
    private String outputSchema;
    private String schemaHash;
    private String riskLevel;
    private String requiredPermissions;
    private String permissionMode;
    private String dataScopePolicy;
    private String retryPolicy;
    private String sideEffect;
    private String confirmationPolicy;
    private Integer maxResultItems;
    private Integer maxResultBytes;
    private Integer timeoutMs;
    private String auditLevel;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
