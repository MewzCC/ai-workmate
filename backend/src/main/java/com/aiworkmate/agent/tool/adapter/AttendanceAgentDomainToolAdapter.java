package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.dto.AttendanceReissueRequest;
import com.aiworkmate.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public final class AttendanceAgentDomainToolAdapter implements AttendanceToolPort {
    private final AttendanceService attendanceService;

    @Override
    public Result query(ToolActorContext context, Query query) {
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

    @Override
    public ReissueWriteResult submitReissue(
            ToolActorContext context, ReissueCommand command, ToolOperationKey operationKey) {
        var item = attendanceService.submitAgentReissue(context.userId(), new AttendanceReissueRequest(
                command.clockDate(), command.clockType(), command.reason()), operationKey.value());
        return new ReissueWriteResult(item.id(), item.status(), item.clockDate(),
                item.clockType(), item.submittedAt());
    }

    private Result today(ToolActorContext context, Query query) {
        var item = attendanceService.getTodayStatus(context.userId());
        var today = new Today(item.id(), item.clockDate(), item.clockInTime(), item.clockOutTime(),
                item.status(), item.lateMinutes(), item.earlyLeaveMinutes(), item.canClockIn(), item.canClockOut());
        return result(query, today, List.of(), List.of(), null, null, 1);
    }

    private Result records(ToolActorContext context, Query query, boolean exceptions) {
        var page = exceptions
                ? attendanceService.listExceptions(context.userId(), query.from(), query.to(), query.employeeId(),
                        query.page(), query.size())
                : attendanceService.listRecords(context.userId(), query.from(), query.to(), query.employeeId(),
                        query.page(), query.size());
        var records = page.records().stream().map(item -> new Record(
                item.id(), item.userName(), item.clockDate(), item.clockInTime(), item.clockOutTime(),
                item.status(), item.lateMinutes(), item.earlyLeaveMinutes())).toList();
        return new Result(query.resource(), null, records, List.of(), null, null,
                page.total(), page.page(), page.size());
    }

    private Result reissues(ToolActorContext context, Query query, boolean pending) {
        var page = pending
                ? attendanceService.listPendingReissues(context.userId(), query.page(), query.size())
                : attendanceService.listMyReissues(context.userId(), query.status(), query.page(), query.size());
        var reissues = page.records().stream().map(item -> new Reissue(
                item.id(), item.applicantName(), item.approverName(), item.clockDate(), item.clockType(),
                item.reason(), item.status(), item.approverComment(), item.submittedAt(), item.decidedAt(),
                item.canDecide(), item.canWithdraw())).toList();
        return new Result(query.resource(), null, List.of(), reissues, null, null,
                page.total(), page.page(), page.size());
    }

    private Result statistics(ToolActorContext context, Query query) {
        var item = attendanceService.getStatistics(context.userId(), query.year(), query.month());
        var personal = item.personal();
        var statistics = new Statistics(item.startDate(), item.endDate(),
                new PersonalStats(personal.userName(), personal.totalDays(), personal.normalDays(),
                        personal.lateDays(), personal.earlyLeaveDays(), personal.missingDays(),
                        personal.pendingReissueCount()),
                item.team().stream().limit(50).map(member -> new TeamStats(
                        member.userName(), member.departmentName(), member.totalDays(), member.normalDays(),
                        member.lateDays(), member.earlyLeaveDays(), member.missingDays())).toList());
        return result(query, null, List.of(), List.of(), statistics, null, 1);
    }

    private Result settings(ToolActorContext context, Query query) {
        var item = attendanceService.getSettings(context.userId());
        var settings = new Settings(item.workStartTime(), item.workEndTime(), item.startFlexMinutes(),
                item.endFlexMinutes(), item.flexLinked(), item.updatedAt());
        return result(query, null, List.of(), List.of(), null, settings, 1);
    }

    private Result result(Query query, Today today, List<Record> records, List<Reissue> reissues,
                          Statistics statistics, Settings settings, long total) {
        return new Result(query.resource(), today, records, reissues, statistics, settings,
                total, query.page(), query.size());
    }
}
