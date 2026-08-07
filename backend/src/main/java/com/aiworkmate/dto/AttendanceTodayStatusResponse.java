package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 今日打卡状态。
 *
 * <p>无论是否打卡都返回，前端用 {@code canClockIn} / {@code canClockOut} 控制按钮可用性。
 */
public record AttendanceTodayStatusResponse(
        Long id,
        LocalDate clockDate,
        LocalDateTime clockInTime,
        LocalDateTime clockOutTime,
        String status,
        Integer lateMinutes,
        Integer earlyLeaveMinutes,
        String clockInIp,
        String clockOutIp,
        boolean canClockIn,
        boolean canClockOut
) {
}
