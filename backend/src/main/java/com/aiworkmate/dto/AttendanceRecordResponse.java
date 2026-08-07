package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录详情（列表/异常查询通用）。
 */
public record AttendanceRecordResponse(
        Long id,
        Long userId,
        String userName,
        LocalDate clockDate,
        LocalDateTime clockInTime,
        LocalDateTime clockOutTime,
        String status,
        Integer lateMinutes,
        Integer earlyLeaveMinutes
) {
}
