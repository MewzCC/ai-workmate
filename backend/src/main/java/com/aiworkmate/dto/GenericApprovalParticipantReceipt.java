package com.aiworkmate.dto;

/** Resource-bound collaboration receipt; contains no frozen form contents. */
public record GenericApprovalParticipantReceipt(long applicationId,long taskId,String status,int version,
        String applicationStatus,String action,long originalAssigneeUserId,long targetUserId,long assigneeUserId) { }
