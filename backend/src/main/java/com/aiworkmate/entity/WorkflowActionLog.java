package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_action_log")
public class WorkflowActionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    private Long taskId;
    private Long actorUserId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private String traceId;
    private LocalDateTime createdAt;
}
