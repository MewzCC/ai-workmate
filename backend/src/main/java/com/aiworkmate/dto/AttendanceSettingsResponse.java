package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤上下班时间配置响应。
 */
public record AttendanceSettingsResponse(
        Long tenantId,
        LocalTime workStartTime,
        LocalTime workEndTime,
        Integer startFlexMinutes,
        Integer endFlexMinutes,
        Boolean flexLinked,
        LocalDateTime updatedAt
) {
}
