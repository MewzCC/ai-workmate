package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApprovalAgentDomainToolAdapter implements TodoToolPort, LeaveToolPort,
        ApprovalConfigurationToolPort, ApprovalTaskToolPort {
    private final LeaveWorkflowService leaveWorkflowService;
    private final ApprovalEngineService approvalEngineService;

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
    public ApprovalConfigurationToolPort.Page query(
            ToolActorContext context, ApprovalConfigurationToolPort.Query query) {
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
        var result = leaveWorkflowService.todos(
                context.userId(), query.status(), query.from(), query.to(), query.page(), query.size());
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
    public LeaveToolPort.WriteResult createDraft(
            ToolActorContext context, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.createAgentDraft(
                context.userId(), leaveRequest(command), operationKey));
    }

    @Override
    public LeaveToolPort.WriteResult submit(ToolActorContext context, long applicationId, int version) {
        return writeResult(leaveWorkflowService.submitAgent(
                context.userId(), applicationId, new VersionRequest(version), context.taskId()));
    }

    @Override
    public LeaveToolPort.WriteResult apply(
            ToolActorContext context, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.applyAgent(
                context.userId(), leaveRequest(command), operationKey));
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
