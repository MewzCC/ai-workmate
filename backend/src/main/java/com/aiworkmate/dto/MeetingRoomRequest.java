package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 会议室新增/修改请求。
 */
public record MeetingRoomRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 200) String location,
        @Min(0) Integer capacity,
        @Size(max = 200) String facilities,
        @Pattern(regexp = "OPEN|CLOSED",
                message = "{validation.meeting.status.invalid}")
        String status,
        @Size(max = 500) String remark
) {
}
