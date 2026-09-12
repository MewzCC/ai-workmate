package com.aiworkmate.agent.tool.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface HrEmployeeToolPort {
    Employee get(long actorUserId, long employeeId);

    record Employee(long id, String name, String role, int status, LocalDateTime createdAt,
                    String departmentName, String positionName, String approverName,
                    List<EmploymentHistory> employmentHistory, Attendance attendance,
                    List<Activity> recentActivities) {
        public Employee {
            employmentHistory = List.copyOf(employmentHistory);
            recentActivities = List.copyOf(recentActivities);
        }
    }
    record EmploymentHistory(long id, String changeType, LocalDate effectiveDate,
                             String targetDepartmentName, String targetPositionName,
                             String targetSupervisorName, LocalDateTime appliedAt) {}
    record Attendance(int totalDays, int normalDays, int lateDays, int earlyLeaveDays,
                      int lateAndEarlyDays, int missingClockDays) {}
    record Activity(long id, String type, String title, String status,
                    LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {}
}
