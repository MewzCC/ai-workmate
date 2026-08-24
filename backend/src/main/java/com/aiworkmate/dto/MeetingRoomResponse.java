package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 会议室详情。
 */
public record MeetingRoomResponse(
        Long id,
        String code,
        String name,
        String location,
        Integer capacity,
        String facilities,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}
