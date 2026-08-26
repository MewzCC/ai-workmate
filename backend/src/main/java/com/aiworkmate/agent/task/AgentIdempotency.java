package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_idempotency")
public class AgentIdempotency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String operation;
    private String idempotencyKey;
    private String requestHash;
    private Long taskId;
    private LocalDateTime createdAt;
}
