package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.TodoQueryService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class TodoAgentDomainToolAdapter implements TodoToolPort {
    private final TodoQueryService queries;

    @Override
    public Page query(ToolActorContext context, Query query) {
        var result = queries.mine(
                context.userId(), context.tenantId(), query.businessType(), query.status(),
                query.from(), query.to(), query.page(), query.size());
        return new Page(result.records().stream().map(item -> new Item(
                item.id(), item.applicationId(), item.applicantName(), item.leaveType(), item.durationHalfDays(),
                item.status(), item.version(), item.submittedAt(), item.dueAt(), item.overdue(),
                item.businessType(),item.title(),item.nodeName(),item.detailAvailable())).toList(),
                result.total(), result.page(), result.size(),result.businessType(),result.status(),result.from(),result.to());
    }
}
