package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡结果。
 */
public record AttendanceClockResponse(
        Long id,
        LocalDate clockDate,
        LocalDateTime clockInTime,
        LocalDateTime clockOutTime,
        String status,
        Integer lateMinutes,
        Integer earlyLeaveMinutes
) {
}
