package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.HrService;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalAgentDomainToolAdapter implements TodoToolPort, LeaveToolPort, KnowledgeToolPort,
        NotificationToolPort, ApprovalConfigurationToolPort, ApprovalTaskToolPort, HrOrganizationToolPort,
        HrEmployeeToolPort, EmployeeChangeToolPort, AssetToolPort, MeetingToolPort {
    private final LeaveWorkflowService leaveWorkflowService;
    private final KnowledgeService knowledgeService;
    private final NotificationService notificationService;
    private final ApprovalEngineService approvalEngineService;
    private final HrService hrService;
    private final EmployeeChangeService employeeChangeService;
    private final AdminAssetsService adminAssetsService;
    private final MeetingBookingService meetingBookingService;

    @Override
    public MeetingToolPort.Result query(ToolActorContext context, MeetingToolPort.Query query) {
        var rooms = adminAssetsService.listMeetingRooms(context.userId(), query.keyword(), query.roomStatus(), 1, 50);
        var bookings = meetingBookingService.listMine(context.userId(), query.from(), query.to(), query.bookingStatus(),
                query.page(), query.size());
        return new MeetingToolPort.Result(rooms.records().stream().map(item -> new MeetingToolPort.Room(
                item.id(), item.code(), item.name(), item.location(), item.capacity(), item.facilities(), item.status(),
                item.remark(), item.canEdit(), item.canDelete())).toList(), bookings.records().stream().map(item ->
                new MeetingToolPort.Booking(item.id(), item.roomId(), item.roomCode(), item.roomName(),
                        item.roomLocation(), item.organizerName(), item.title(), item.agenda(), item.startAt(),
                        item.endAt(), item.attendeeCount(), item.status(), item.version(), item.cancelledByName(),
                        item.cancelledAt(), item.cancelReason(), item.createdAt(), item.updatedAt(), item.canCancel()))
                .toList(), bookings.total(), bookings.page(), bookings.size());
    }

    @Override
    public AssetToolPort.Page query(ToolActorContext context, AssetToolPort.Query query) {
        var result = adminAssetsService.listAssets(context.userId(), query.keyword(), query.category(), query.status(),
                query.page(), query.size());
        return new AssetToolPort.Page(result.records().stream().map(item -> new AssetToolPort.Item(
                item.id(), item.assetCode(), item.name(), item.category(), item.specification(), item.status(),
                item.departmentName(), item.ownerName(), item.purchaseDate(), item.originalValue(), item.remark(),
                item.version(), item.canEdit(), item.canDelete(), item.createdAt(), item.updatedAt())).toList(),
                result.total(), result.page(), result.size());
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
        var departments = overview.departments().stream().filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new HrOrganizationToolPort.Department(
                        item.id(), item.code(), item.name(), item.parentId(), item.status())).toList();
        var positions = overview.positions().stream().filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new HrOrganizationToolPort.Position(
                        item.id(), item.code(), item.name(), item.status())).toList();
        var employees = overview.employees().stream().filter(item -> matches.test(item.name()) || matches.test(item.role()))
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

    @Override
    public ApprovalTaskToolPort.Page query(ToolActorContext context, ApprovalTaskToolPort.Query query) {
        var result = leaveWorkflowService.adminList(context.userId(), query.status(), query.from(), query.to(),
                query.keyword(), query.leaveType(), query.page(), query.size());
        return new ApprovalTaskToolPort.Page(result.records().stream().map(item ->
                new ApprovalTaskToolPort.Item(item.id(), item.taskId(), item.applicantName(), item.approverName(),
                        item.leaveType(), item.durationDays(), item.status(), item.version(), item.submittedAt(),
                        item.taskDueAt(), item.overdue())).toList(), result.total(), result.page(), result.size());
    }

    @Override
    public ApprovalConfigurationToolPort.Page query(ToolActorContext context, ApprovalConfigurationToolPort.Query query) {
        return switch (query.resource()) {
            case FORM -> {
                var result = approvalEngineService.listForms(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.FORM,
                                item.formKey(), item.formName(), item.description(), item.status(), item.version(),
                                null, null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case PROCESS -> {
                var result = approvalEngineService.listProcesses(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.PROCESS,
                                item.processKey(), item.processName(), item.description(), item.status(), item.version(),
                                item.formName(), null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case RULE -> {
                var result = approvalEngineService.listRules(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.RULE,
                                item.ruleKey(), item.ruleName(), item.description(), item.status(), item.version(),
                                null, item.ruleType(), item.priority(), item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
        };
    }

    @Override
    public TodoToolPort.Page query(ToolActorContext context, TodoToolPort.Query query) {
        var result = leaveWorkflowService.todos(context.userId(), query.status(), query.from(), query.to(), query.page(), query.size());
        return new TodoToolPort.Page(result.records().stream().map(item -> new TodoToolPort.Item(
                item.id(), item.applicationId(), item.applicantName(), item.leaveType(), item.durationHalfDays(),
                item.status(), item.version(), item.submittedAt(), item.dueAt(), item.overdue())).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public LeaveToolPort.Page mine(ToolActorContext context, LeaveToolPort.Query query) {
        var result = leaveWorkflowService.mine(context.userId(), query.status(), query.page(), query.size());
        return new LeaveToolPort.Page(result.records().stream().map(this::leaveItem).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public LeaveToolPort.Item getMine(ToolActorContext context, long applicationId) {
        return leaveItem(leaveWorkflowService.getMine(context.userId(), applicationId));
    }

    @Override
    public LeaveToolPort.WriteResult createDraft(ToolActorContext context, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.createAgentDraft(context.userId(), leaveRequest(command), operationKey));
    }

    @Override
    public LeaveToolPort.WriteResult submit(ToolActorContext context, long applicationId, int version) {
        return writeResult(leaveWorkflowService.submitAgent(
                context.userId(), applicationId, new VersionRequest(version), context.taskId()));
    }

    @Override
    public LeaveToolPort.WriteResult apply(ToolActorContext context, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.applyAgent(context.userId(), leaveRequest(command), operationKey));
    }

    @Override
    public KnowledgeToolPort.Result search(ToolActorContext context, KnowledgeToolPort.Query query) {
        var response = knowledgeService.search(context.userId(),
                new KnowledgeSearchRequest(query.text(), query.topK(), query.minScore()));
        return new KnowledgeToolPort.Result(response.records().stream().map(item -> new KnowledgeToolPort.Item(
                item.content(), item.score(), item.matchType(), item.docId(), item.chunkId(),
                item.filename(), item.chunkIndex())).toList());
    }

    @Override
    public NotificationToolPort.Page mine(ToolActorContext context, int page, int size) {
        var result = notificationService.list(context.userId(), page, size);
        return new NotificationToolPort.Page(result.records().stream().map(item -> new NotificationToolPort.Item(
                item.id(), item.type(), item.title(), item.content(), item.bizType(), item.read(), item.createdAt())).toList(),
                result.total(), result.page(), result.size());
    }

    private LeaveApplicationRequest leaveRequest(LeaveToolPort.Draft command) {
        return new LeaveApplicationRequest(command.leaveType(), command.approverUserId(), command.startDate(),
                command.startPeriod(), command.endDate(), command.endPeriod(), command.reason(), null);
    }

    private LeaveToolPort.Item leaveItem(LeaveApplicationResponse item) {
        return new LeaveToolPort.Item(item.id(), item.approverName(), item.leaveType(), item.startDate(),
                item.startPeriod(), item.endDate(), item.endPeriod(), item.durationHalfDays(), item.durationDays(),
                item.reason(), item.status(), item.version(), item.submittedAt(), item.completedAt(),
                item.createdAt(), item.updatedAt());
    }

    private LeaveToolPort.WriteResult writeResult(LeaveApplicationResponse item) {
        return new LeaveToolPort.WriteResult(item.id(), item.status(), item.version(), item.taskId());
    }
}
