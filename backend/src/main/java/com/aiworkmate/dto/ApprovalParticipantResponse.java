package com.aiworkmate.dto;

/** 审批协作候选用户，仅返回展示所需的安全字段。 */
public record ApprovalParticipantResponse(
        Long id,
        String name,
        String avatarUrl,
        boolean canApprove
) {
}
