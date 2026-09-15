package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class TodoAgentDomainToolAdapter implements TodoToolPort {
    private final LeaveWorkflowService leaveWorkflowService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        var result = leaveWorkflowService.todos(
                context.userId(), query.status(), query.from(), query.to(), query.page(), query.size());
        return new Page(result.records().stream().map(item -> new Item(
                item.id(), item.applicationId(), item.applicantName(), item.leaveType(), item.durationHalfDays(),
                item.status(), item.version(), item.submittedAt(), item.dueAt(), item.overdue())).toList(),
                result.total(), result.page(), result.size());
    }
}
