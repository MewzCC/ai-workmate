package com.aiworkmate.dto;

/** Original and added task coordinates are intentionally distinct. All decisions remain pending. */
public record GenericApprovalAddSignReceipt(long applicationId,long taskId,String status,int version,String applicationStatus,
        String action,String mode,long targetUserId,long addedTaskId,String addedTaskStatus,int addedTaskVersion,long activeTaskId) { }
