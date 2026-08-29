package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record WorkflowTimelineResponse(
        Long id,
        Long actorUserId,
        String actorName,
        Long originalAssigneeUserId,
        String originalAssigneeName,
        Long targetUserId,
        String targetUserName,
        String action,
        String fromStatus,
        String toStatus,
        String comment,
        LocalDateTime createdAt,
        String actorAvatar,
        LocalDateTime actorUpdatedAt,
        String actorAvatarUrl
) {
}
