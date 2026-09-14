package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.AttendanceService;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class HrAgentDomainToolAdapter implements HrOrganizationToolPort, HrEmployeeToolPort,
        EmployeeChangeToolPort, AttendanceToolPort {
    private final HrService hrService;
    private final EmployeeChangeService employeeChangeService;
    private final AttendanceService attendanceService;

    @Override
    public AttendanceToolPort.Result query(ToolActorContext context, AttendanceToolPort.Query query) {
        return switch (query.resource()) {
            case TODAY -> today(context, query);
            case RECORDS -> records(context, query, false);
            case EXCEPTIONS -> records(context, query, true);
            case MY_REISSUES -> reissues(context, query, false);
            case PENDING_REISSUES -> reissues(context, query, true);
            case STATISTICS -> statistics(context, query);
            case SETTINGS -> settings(context, query);
        };
    }

    private AttendanceToolPort.Result today(ToolActorContext context, AttendanceToolPort.Query query) {
        var item = attendanceService.getTodayStatus(context.userId());
        var today = new AttendanceToolPort.Today(item.id(), item.clockDate(), item.clockInTime(),
                item.clockOutTime(), item.status(), item.lateMinutes(), item.earlyLeaveMinutes(),
                item.canClockIn(), item.canClockOut());
        return attendanceResult(query, today, List.of(), List.of(), null, null, 1);
    }

    private AttendanceToolPort.Result records(ToolActorContext context, AttendanceToolPort.Query query,
                                              boolean exceptions) {
        var result = exceptions
                ? attendanceService.listExceptions(context.userId(), query.from(), query.to(), query.employeeId(),
                        query.page(), query.size())
                : attendanceService.listRecords(context.userId(), query.from(), query.to(), query.employeeId(),
                        query.page(), query.size());
        var records = result.records().stream().map(item -> new AttendanceToolPort.Record(
                item.id(), item.userName(), item.clockDate(), item.clockInTime(), item.clockOutTime(),
                item.status(), item.lateMinutes(), item.earlyLeaveMinutes())).toList();
        return new AttendanceToolPort.Result(query.resource(), null, records, List.of(), null, null,
                result.total(), result.page(), result.size());
    }

    private AttendanceToolPort.Result reissues(ToolActorContext context, AttendanceToolPort.Query query,
                                               boolean pending) {
        var result = pending
                ? attendanceService.listPendingReissues(context.userId(), query.page(), query.size())
                : attendanceService.listMyReissues(context.userId(), query.status(), query.page(), query.size());
        var reissues = result.records().stream().map(item -> new AttendanceToolPort.Reissue(
                item.id(), item.applicantName(), item.approverName(), item.clockDate(), item.clockType(),
                item.reason(), item.status(), item.approverComment(), item.submittedAt(), item.decidedAt(),
                item.canDecide(), item.canWithdraw())).toList();
        return new AttendanceToolPort.Result(query.resource(), null, List.of(), reissues, null, null,
                result.total(), result.page(), result.size());
    }

    private AttendanceToolPort.Result statistics(ToolActorContext context, AttendanceToolPort.Query query) {
        var item = attendanceService.getStatistics(context.userId(), query.year(), query.month());
        var personal = item.personal();
        var stats = new AttendanceToolPort.Statistics(item.startDate(), item.endDate(),
                new AttendanceToolPort.PersonalStats(personal.userName(), personal.totalDays(),
                        personal.normalDays(), personal.lateDays(), personal.earlyLeaveDays(),
                        personal.missingDays(), personal.pendingReissueCount()),
                item.team().stream().limit(50).map(member -> new AttendanceToolPort.TeamStats(
                        member.userName(), member.departmentName(), member.totalDays(), member.normalDays(),
                        member.lateDays(), member.earlyLeaveDays(), member.missingDays())).toList());
        return attendanceResult(query, null, List.of(), List.of(), stats, null, 1);
    }

    private AttendanceToolPort.Result settings(ToolActorContext context, AttendanceToolPort.Query query) {
        var item = attendanceService.getSettings(context.userId());
        var settings = new AttendanceToolPort.Settings(item.workStartTime(), item.workEndTime(),
                item.startFlexMinutes(), item.endFlexMinutes(), item.flexLinked(), item.updatedAt());
        return attendanceResult(query, null, List.of(), List.of(), null, settings, 1);
    }

    private AttendanceToolPort.Result attendanceResult(AttendanceToolPort.Query query, AttendanceToolPort.Today today,
                                                       List<AttendanceToolPort.Record> records,
                                                       List<AttendanceToolPort.Reissue> reissues,
                                                       AttendanceToolPort.Statistics statistics,
                                                       AttendanceToolPort.Settings settings, long total) {
        return new AttendanceToolPort.Result(query.resource(), today, records, reissues, statistics, settings,
                total, query.page(), query.size());
    }

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
