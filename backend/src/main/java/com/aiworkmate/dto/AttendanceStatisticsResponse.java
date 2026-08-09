package com.aiworkmate.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤统计。
 *
 * <p>普通员工仅返回 {@code personal}；管理员额外返回 {@code team} 团队成员统计。
 */
public record AttendanceStatisticsResponse(
        LocalDate startDate,
        LocalDate endDate,
        PersonalStats personal,
        List<TeamMemberStats> team
) {

    /**
     * 个人统计。
     */
    public record PersonalStats(
            Long userId,
            String userName,
            int totalDays,
            int normalDays,
            int lateDays,
            int earlyLeaveDays,
            int missingDays,
            int pendingReissueCount
    ) {
    }

    /**
     * 团队成员统计（管理员视图）。
     */
    public record TeamMemberStats(
            Long userId,
            String userName,
            String departmentName,
            int totalDays,
            int normalDays,
            int lateDays,
            int earlyLeaveDays,
            int missingDays
    ) {
    }
}
