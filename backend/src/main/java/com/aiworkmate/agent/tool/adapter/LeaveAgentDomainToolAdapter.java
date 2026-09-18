package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class LeaveAgentDomainToolAdapter implements LeaveToolPort {
    private final LeaveWorkflowService leaveWorkflowService;

    @Override
    public Page mine(ToolActorContext context, Query query) {
        var result = leaveWorkflowService.mine(context.userId(), query.status(), query.page(), query.size());
        return new Page(result.records().stream().map(this::leaveItem).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public Item getMine(ToolActorContext context, long applicationId) {
        return leaveItem(leaveWorkflowService.getMine(context.userId(), applicationId));
    }

    @Override
    public WriteResult createDraft(ToolActorContext context, Draft command, ToolOperationKey operationKey) {
        return writeResult(leaveWorkflowService.createAgentDraft(
                context.userId(), leaveRequest(command), operationKey.value()));
    }

    @Override
    public WriteResult submit(ToolActorContext context, long applicationId, int version) {
        return writeResult(leaveWorkflowService.submitAgent(
                context.userId(), applicationId, new VersionRequest(version), context.taskId()));
    }

    @Override
    public WriteResult apply(ToolActorContext context, Draft command, ToolOperationKey operationKey) {
        return writeResult(leaveWorkflowService.applyAgent(
                context.userId(), leaveRequest(command), operationKey.value()));
    }

    @Override
    public WithdrawalResult withdraw(ToolActorContext context, long applicationId, int version) {
        var item = leaveWorkflowService.withdraw(context.userId(), applicationId, new VersionRequest(version));
        return new WithdrawalResult(item.id(), item.status(), item.version());
    }

    private LeaveApplicationRequest leaveRequest(Draft command) {
        return new LeaveApplicationRequest(command.leaveType(), command.approverUserId(), command.startDate(),
                command.startPeriod(), command.endDate(), command.endPeriod(), command.reason(), null);
    }

    private Item leaveItem(LeaveApplicationResponse item) {
        return new Item(item.id(), item.approverName(), item.leaveType(), item.startDate(),
                item.startPeriod(), item.endDate(), item.endPeriod(), item.durationHalfDays(), item.durationDays(),
                item.reason(), item.status(), item.version(), item.submittedAt(), item.completedAt(),
                item.createdAt(), item.updatedAt());
    }

    private WriteResult writeResult(LeaveApplicationResponse item) {
        return new WriteResult(item.id(), item.status(), item.version(), item.taskId());
    }
}
