package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.ApprovalEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalAgentDomainToolAdapter implements TodoToolPort, LeaveToolPort, KnowledgeToolPort,
        NotificationToolPort, ApprovalConfigurationToolPort, ApprovalTaskToolPort {
    private final LeaveWorkflowService leaveWorkflowService;
    private final KnowledgeService knowledgeService;
    private final NotificationService notificationService;
    private final ApprovalEngineService approvalEngineService;

    @Override
    public ApprovalTaskToolPort.Page query(Long actorUserId, ApprovalTaskToolPort.Query query) {
        var result = leaveWorkflowService.adminList(actorUserId, query.status(), query.from(), query.to(),
                query.keyword(), query.leaveType(), query.page(), query.size());
        return new ApprovalTaskToolPort.Page(result.records().stream().map(item ->
                new ApprovalTaskToolPort.Item(item.id(), item.taskId(), item.applicantName(), item.approverName(),
                        item.leaveType(), item.durationDays(), item.status(), item.version(), item.submittedAt(),
                        item.taskDueAt(), item.overdue())).toList(), result.total(), result.page(), result.size());
    }

    @Override
    public ApprovalConfigurationToolPort.Page query(Long actorUserId, ApprovalConfigurationToolPort.Query query) {
        return switch (query.resource()) {
            case FORM -> {
                var result = approvalEngineService.listForms(
                        actorUserId, query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.FORM,
                                item.formKey(), item.formName(), item.description(), item.status(), item.version(),
                                null, null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case PROCESS -> {
                var result = approvalEngineService.listProcesses(
                        actorUserId, query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.PROCESS,
                                item.processKey(), item.processName(), item.description(), item.status(), item.version(),
                                item.formName(), null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case RULE -> {
                var result = approvalEngineService.listRules(
                        actorUserId, query.keyword(), query.status(), query.page(), query.size());
                yield new ApprovalConfigurationToolPort.Page(result.records().stream().map(item ->
                        new ApprovalConfigurationToolPort.Item(item.id(), ApprovalConfigurationToolPort.Resource.RULE,
                                item.ruleKey(), item.ruleName(), item.description(), item.status(), item.version(),
                                null, item.ruleType(), item.priority(), item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
        };
    }

    @Override
    public TodoToolPort.Page query(Long actorUserId, TodoToolPort.Query query) {
        var result = leaveWorkflowService.todos(actorUserId, query.status(), query.from(), query.to(), query.page(), query.size());
        return new TodoToolPort.Page(result.records().stream().map(item -> new TodoToolPort.Item(
                item.id(), item.applicationId(), item.applicantName(), item.leaveType(), item.durationHalfDays(),
                item.status(), item.version(), item.submittedAt(), item.dueAt(), item.overdue())).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public LeaveToolPort.Page mine(Long actorUserId, LeaveToolPort.Query query) {
        var result = leaveWorkflowService.mine(actorUserId, query.status(), query.page(), query.size());
        return new LeaveToolPort.Page(result.records().stream().map(this::leaveItem).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public LeaveToolPort.Item getMine(Long actorUserId, long applicationId) {
        return leaveItem(leaveWorkflowService.getMine(actorUserId, applicationId));
    }

    @Override
    public LeaveToolPort.WriteResult createDraft(Long actorUserId, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.createAgentDraft(actorUserId, leaveRequest(command), operationKey));
    }

    @Override
    public LeaveToolPort.WriteResult submit(Long actorUserId, long applicationId, int version, long taskId) {
        return writeResult(leaveWorkflowService.submitAgent(actorUserId, applicationId, new VersionRequest(version), taskId));
    }

    @Override
    public LeaveToolPort.WriteResult apply(Long actorUserId, LeaveToolPort.Draft command, String operationKey) {
        return writeResult(leaveWorkflowService.applyAgent(actorUserId, leaveRequest(command), operationKey));
    }

    @Override
    public KnowledgeToolPort.Result search(Long actorUserId, KnowledgeToolPort.Query query) {
        var response = knowledgeService.search(actorUserId,
                new KnowledgeSearchRequest(query.text(), query.topK(), query.minScore()));
        return new KnowledgeToolPort.Result(response.records().stream().map(item -> new KnowledgeToolPort.Item(
                item.content(), item.score(), item.matchType(), item.docId(), item.chunkId(),
                item.filename(), item.chunkIndex())).toList());
    }

    @Override
    public NotificationToolPort.Page mine(Long actorUserId, int page, int size) {
        var result = notificationService.list(actorUserId, page, size);
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
