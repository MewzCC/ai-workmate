package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.ApprovalAddSignRequest;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.ApprovalParticipantRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class ApprovalTaskAgentDomainToolAdapter implements ApprovalTaskToolPort {
    private final LeaveWorkflowService leaveWorkflowService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        var result = leaveWorkflowService.adminList(context.userId(), query.status(), query.from(), query.to(),
                query.keyword(), query.leaveType(), query.page(), query.size());
        return new Page(result.records().stream().map(item ->
                new Item(item.id(), item.taskId(), item.applicantName(), item.approverName(), item.leaveType(),
                        item.durationDays(), item.status(), item.version(), item.submittedAt(), item.taskDueAt(),
                        item.overdue())).toList(), result.total(), result.page(), result.size());
    }

    @Override
    public WriteResult approve(ToolActorContext context, DecisionCommand command) {
        return result(command.taskId(), "APPROVE", leaveWorkflowService.approve(context.userId(), command.taskId(),
                new ApprovalDecisionRequest(command.version(), command.comment())));
    }

    @Override
    public WriteResult reject(ToolActorContext context, DecisionCommand command) {
        return result(command.taskId(), "REJECT", leaveWorkflowService.reject(context.userId(), command.taskId(),
                new ApprovalDecisionRequest(command.version(), command.comment())));
    }

    @Override
    public WriteResult transfer(ToolActorContext context, ParticipantCommand command) {
        return result(command.taskId(), "TRANSFER", leaveWorkflowService.transfer(context.userId(), command.taskId(),
                new ApprovalParticipantRequest(command.targetUserId(), command.version(), command.reason())));
    }

    @Override
    public WriteResult copyTo(ToolActorContext context, ParticipantCommand command) {
        return result(command.taskId(), "COPY", leaveWorkflowService.copyTo(context.userId(), command.taskId(),
                new ApprovalParticipantRequest(command.targetUserId(), command.version(), command.reason())));
    }

    @Override
    public WriteResult addSign(ToolActorContext context, AddSignCommand command) {
        return result(command.taskId(), "ADD_SIGN_" + command.mode(), leaveWorkflowService.addSign(
                context.userId(), command.taskId(), new ApprovalAddSignRequest(command.targetUserId(),
                        command.version(), command.mode(), command.reason())));
    }

    private WriteResult result(long taskId, String action, LeaveApplicationResponse response) {
        return new WriteResult(response.id(), taskId, response.status(), response.version(), action);
    }
}
