package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record GenericApprovalAddSignItem(long taskId,long parentTaskId,String mode,long assigneeUserId,String assigneeName,
        String status,int version,LocalDateTime dueAt) { }
