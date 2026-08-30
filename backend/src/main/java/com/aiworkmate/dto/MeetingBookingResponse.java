package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record MeetingBookingResponse(
        Long id,
        Long roomId,
        String roomCode,
        String roomName,
        String roomLocation,
        Long organizerUserId,
        String organizerName,
        String title,
        String agenda,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer attendeeCount,
        String status,
        Integer version,
        Long cancelledByUserId,
        String cancelledByName,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canCancel
) {
}
