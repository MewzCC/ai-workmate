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

    @Override public Detail detailAssigned(ToolActorContext context, DetailQuery query) {
        var detail = service.detailAssignedForAgent(context.userId(), context.tenantId(), query.taskId(), query.fieldPage());
        var task = detail.task();
        return new Detail(task.id(), task.applicationId(), task.applicantName(), task.formName(), task.title(),
                task.nodeName(), task.status(), task.version(), detail.instanceStatus(), detail.applicationStatus(),
                task.submittedAt(), task.dueAt(), task.overdue(), detail.fields().stream()
                    .map(field -> new ApprovalApplicationToolPort.DetailField(field.name(), field.label(), field.type(), field.values(), field.redacted())).toList(),
                detail.totalFields(), detail.fieldPage(), GenericApprovalTaskService.AGENT_FIELD_PAGE_SIZE,
                detail.canApprove(), detail.canReject(), detail.blockedReason());
    }
}
