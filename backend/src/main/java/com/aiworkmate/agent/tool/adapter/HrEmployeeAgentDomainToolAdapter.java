package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class HrEmployeeAgentDomainToolAdapter implements HrEmployeeToolPort {
    private final HrService hrService;

    @Override
    public Employee get(ToolActorContext context, long employeeId) {
        var item = hrService.employeeDetailForActor(context.userId(), employeeId);
        var attendance = item.attendance();
        return new Employee(item.id(), item.name(), item.role(), item.status(), item.createdAt(),
                item.departmentName(), item.positionName(), item.approverName(),
                item.employmentHistory().stream().map(history -> new EmploymentHistory(
                        history.id(), history.changeType(), history.effectiveDate(), history.targetDepartmentName(),
                        history.targetPositionName(), history.targetSupervisorName(), history.appliedAt())).toList(),
                new Attendance(attendance.totalDays(), attendance.normalDays(), attendance.lateDays(),
                        attendance.earlyLeaveDays(), attendance.lateAndEarlyDays(), attendance.missingClockDays()),
                item.recentActivities().stream().map(activity -> new Activity(
                        activity.id(), activity.type(), activity.title(), activity.status(), activity.startDate(),
                        activity.endDate(), activity.createdAt())).toList());
    }
}
