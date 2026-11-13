package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.GenericApprovalTaskService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class GenericApprovalTaskAgentDomainToolAdapter implements GenericApprovalTaskToolPort {
    private final GenericApprovalTaskService service;
    @Override public AddSignResult addSign(ToolActorContext context,com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.AddSignCommand command){
        var result=service.addSign(context.userId(),context.tenantId(),command.taskId(),new com.aiworkmate.dto.ApprovalAddSignRequest(command.targetUserId(),command.version(),command.mode(),command.reason()));
        return new AddSignResult(result.applicationId(),result.taskId(),result.status(),result.version(),result.applicationStatus(),result.action(),result.mode(),result.targetUserId(),
                result.addedTaskId(),result.addedTaskStatus(),result.addedTaskVersion(),result.activeTaskId());
    }
    @Override public com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.Participants participants(ToolActorContext context,ParticipantQuery query){
        var result=service.participants(context.userId(),context.tenantId(),query.taskId(),query.purpose(),
                new com.aiworkmate.service.model.BusinessCandidateQuery(query.keyword(),query.page(),query.size()));
        return new com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.Participants(result.records().stream().map(row->
                new com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.Participant(row.id(),row.name(),row.canApprove())).toList(),result.total(),result.page(),result.size(),!"CC".equals(query.purpose()));
    }
    @Override public ParticipantResult transfer(ToolActorContext context,com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.ParticipantCommand command){
        return participant(service.transfer(context.userId(),context.tenantId(),command.taskId(),new com.aiworkmate.dto.ApprovalParticipantRequest(command.targetUserId(),command.version(),command.reason())));
    }
    @Override public ParticipantResult copyTo(ToolActorContext context,com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.ParticipantCommand command){
        return participant(service.copyTo(context.userId(),context.tenantId(),command.taskId(),new com.aiworkmate.dto.ApprovalParticipantRequest(command.targetUserId(),command.version(),command.reason())));
    }
    private ParticipantResult participant(com.aiworkmate.dto.GenericApprovalParticipantReceipt result){
        return new ParticipantResult(result.applicationId(),result.taskId(),result.status(),result.version(),result.applicationStatus(),result.action(),result.originalAssigneeUserId(),result.targetUserId(),result.assigneeUserId());
    }

    @Override public WriteResult approve(ToolActorContext context, DecisionCommand command) {
        var result = service.approve(context.userId(), context.tenantId(), command.taskId(),
                new com.aiworkmate.dto.ApprovalDecisionRequest(command.version(), command.comment()));
        return new WriteResult(result.task().applicationId(), result.task().id(), result.task().status(),
                result.task().version(), result.applicationStatus(), "APPROVE");
    }

    @Override public WriteResult reject(ToolActorContext context, DecisionCommand command) {
        var result = service.reject(context.userId(), context.tenantId(), command.taskId(),
                new com.aiworkmate.dto.ApprovalDecisionRequest(command.version(), command.comment()));
        return new WriteResult(result.task().applicationId(), result.task().id(), result.task().status(),
                result.task().version(), result.applicationStatus(), "REJECT");
    }

    @Override public Detail detailAssigned(ToolActorContext context, DetailQuery query) {
        var detail = service.detailAssignedForAgent(context.userId(), context.tenantId(), query.taskId(), query.fieldPage());
        var task = detail.task();
        return new Detail(task.id(), task.applicationId(), task.applicantName(), task.formName(), task.title(),
                task.nodeName(), task.status(), task.version(), detail.instanceStatus(), detail.applicationStatus(),
                task.submittedAt(), task.dueAt(), task.overdue(), detail.fields().stream()
                    .map(ApprovalApplicationFieldMapping::map).toList(),
                detail.totalFields(), detail.fieldPage(), GenericApprovalTaskService.AGENT_FIELD_PAGE_SIZE,
                detail.canApprove(), detail.canReject(), detail.blockedReason(),task.parentTaskId(),task.addSignMode(),detail.canAddSign());
    }
}
