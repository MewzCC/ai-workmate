package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
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
}
