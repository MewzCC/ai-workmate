package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工档案详情响应。
 *
 * <p>聚合某员工在指定租户下的个人基本信息、任职/组织信息、
 * 考勤概览（打卡记录状态分布）与近期申请记录（请假/补卡）。
 */
public record EmployeeDetailResponse(
        Long id,
        String name,
        String email,
        String role,
        Integer status,
        String avatarUrl,
        LocalDateTime createdAt,
        Long departmentId,
        String departmentName,
        Long positionId,
        String positionName,
        Long approverUserId,
        String approverName,
        String approverAvatarUrl,
        List<EmploymentHistoryRecord> employmentHistory,
        AttendanceOverview attendance,
        List<ActivityRecord> recentActivities
) {

    /** 已生效的入转调离任职历史。 */
    public record EmploymentHistoryRecord(
            Long id,
            String changeType,
            LocalDate effectiveDate,
            String currentDepartmentName,
            String currentPositionName,
            String currentSupervisorName,
            String targetDepartmentName,
            String targetPositionName,
            String targetSupervisorName,
            String reason,
            LocalDateTime appliedAt
    ) {
    }

    /** 考勤概览：基于 {@code attendance_record} 的状态分布统计 */
    public record AttendanceOverview(
            Integer totalDays,
            Integer normalDays,
            Integer lateDays,
            Integer earlyLeaveDays,
            Integer lateAndEarlyDays,
            Integer missingClockDays
    ) {
    }

    /** 近期申请记录：请假单 / 补卡申请 */
    public record ActivityRecord(
            Long id,
            String type,
            String title,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime createdAt
    ) {
    }
}
