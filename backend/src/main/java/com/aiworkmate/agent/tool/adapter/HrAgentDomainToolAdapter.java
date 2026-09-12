package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class HrAgentDomainToolAdapter implements HrOrganizationToolPort, HrEmployeeToolPort,
        EmployeeChangeToolPort {
    private final HrService hrService;
    private final EmployeeChangeService employeeChangeService;

    @Override
    public EmployeeChangeToolPort.Page query(ToolActorContext context, EmployeeChangeToolPort.Query query) {
        var result = employeeChangeService.list(context.userId(), query.status(), query.changeType(), query.keyword(),
                query.page(), query.size());
        return new EmployeeChangeToolPort.Page(result.records().stream().map(item ->
                new EmployeeChangeToolPort.Item(item.id(), item.employeeName(), item.applicantName(),
                        item.reviewApproverName(), item.changeType(), item.effectiveDate(),
                        item.currentDepartmentName(), item.currentPositionName(), item.targetDepartmentName(),
                        item.targetPositionName(), item.targetSupervisorName(), item.reason(), item.status(),
                        item.version(), item.canApprove(), item.canWithdraw(), item.submittedAt(), item.decidedAt(),
                        item.appliedAt())).toList(), result.total(), result.page(), result.size());
    }

    @Override
    public HrOrganizationToolPort.Result query(ToolActorContext context, HrOrganizationToolPort.Query query) {
        var overview = hrService.overviewForActor(context.userId());
        Predicate<String> matches = value -> query.keyword() == null ||
                (value != null && value.toLowerCase(Locale.ROOT).contains(query.keyword().toLowerCase(Locale.ROOT)));
        var departments = overview.departments().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new HrOrganizationToolPort.Department(
                        item.id(), item.code(), item.name(), item.parentId(), item.status())).toList();
        var positions = overview.positions().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new HrOrganizationToolPort.Position(
                        item.id(), item.code(), item.name(), item.status())).toList();
        var employees = overview.employees().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.role()))
                .limit(query.limit()).map(item -> new HrOrganizationToolPort.Employee(
                        item.id(), item.name(), item.role(), item.status(), item.departmentId(), item.positionId(),
                        item.approverName())).toList();
        return new HrOrganizationToolPort.Result(departments, positions, employees);
    }

    @Override
    public HrEmployeeToolPort.Employee get(ToolActorContext context, long employeeId) {
        var item = hrService.employeeDetailForActor(context.userId(), employeeId);
        var attendance = item.attendance();
        return new HrEmployeeToolPort.Employee(item.id(), item.name(), item.role(), item.status(), item.createdAt(),
                item.departmentName(), item.positionName(), item.approverName(),
                item.employmentHistory().stream().map(history -> new HrEmployeeToolPort.EmploymentHistory(
                        history.id(), history.changeType(), history.effectiveDate(), history.targetDepartmentName(),
                        history.targetPositionName(), history.targetSupervisorName(), history.appliedAt())).toList(),
                new HrEmployeeToolPort.Attendance(attendance.totalDays(), attendance.normalDays(), attendance.lateDays(),
                        attendance.earlyLeaveDays(), attendance.lateAndEarlyDays(), attendance.missingClockDays()),
                item.recentActivities().stream().map(activity -> new HrEmployeeToolPort.Activity(
                        activity.id(), activity.type(), activity.title(), activity.status(), activity.startDate(),
                        activity.endDate(), activity.createdAt())).toList());
    }
}
