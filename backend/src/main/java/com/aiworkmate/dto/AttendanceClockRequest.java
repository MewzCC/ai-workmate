package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 打卡请求。
 *
 * <p>{@code CLOCK_IN} 上班打卡；{@code CLOCK_OUT} 下班打卡。
 */
public record AttendanceClockRequest(
        @NotBlank
        @Pattern(regexp = "CLOCK_IN|CLOCK_OUT", message = "{validation.attendance.clockType.invalid}")
        String clockType
) {
}
