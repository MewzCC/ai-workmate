package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 考勤上下班时间配置请求。
 */
public record AttendanceSettingsRequest(
        @NotNull(message = "{validation.attendance.workStartTime.required}")
        LocalTime workStartTime,

        @NotNull(message = "{validation.attendance.workEndTime.required}")
        LocalTime workEndTime,

        @NotNull(message = "{validation.attendance.flexStart.required}")
        @Min(value = 0, message = "{validation.attendance.flex.invalid}")
        @Max(value = 480, message = "{validation.attendance.flex.invalid}")
        Integer startFlexMinutes,

        @NotNull(message = "{validation.attendance.flexEnd.required}")
        @Min(value = 0, message = "{validation.attendance.flex.invalid}")
        @Max(value = 480, message = "{validation.attendance.flex.invalid}")
        Integer endFlexMinutes,

        @NotNull(message = "{validation.attendance.flexLinked.required}")
        Boolean flexLinked
) {
}
