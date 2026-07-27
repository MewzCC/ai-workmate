package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_task")
public class WorkflowTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    private String businessType;
    private Long businessId;
    private Long assigneeUserId;
    private String status;
    private String decisionComment;
    private LocalDateTime dueAt;
    private Integer version;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
