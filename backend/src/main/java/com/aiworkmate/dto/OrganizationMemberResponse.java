package com.aiworkmate.dto;

public record OrganizationMemberResponse(
        Long id,
        String name,
        Long departmentId,
        Long positionId,
        Long approverUserId
) {
}
