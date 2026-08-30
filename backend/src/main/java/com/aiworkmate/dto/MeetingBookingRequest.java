package com.aiworkmate.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record MeetingBookingRequest(
        @NotNull(message = "{validation.meeting.booking.room.required}") Long roomId,
        @NotBlank(message = "{validation.meeting.booking.title.required}") @Size(max = 120) String title,
        @Size(max = 500) String agenda,
        @NotNull(message = "{validation.meeting.booking.start.required}")
        @Future(message = "{validation.meeting.booking.start.future}") LocalDateTime startAt,
        @NotNull(message = "{validation.meeting.booking.end.required}") LocalDateTime endAt,
        @NotNull(message = "{validation.meeting.booking.attendee.required}")
        @Min(value = 1, message = "{validation.meeting.booking.attendee.invalid}")
        @Max(value = 10000, message = "{validation.meeting.booking.attendee.invalid}") Integer attendeeCount
) {
}
