package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AgentTaskQueryService;
import com.aiworkmate.service.AgentTaskCommandService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class AgentTaskCenterAgentDomainToolAdapter implements AgentTaskCenterToolPort {
    private final AgentTaskQueryService queryService;
    private final AgentTaskCommandService commandService;

    @Override
    public TaskPage mine(ToolActorContext context, TaskQuery query) {
        var page = queryService.mine(context.userId(), query.status(), query.from(), query.to(), query.page(), query.size());
        return new TaskPage(page.records().stream().map(x -> new Task(x.taskId(), x.pageId(), x.status(),
                x.riskLevel(), x.planVersion(), x.createdAt(), x.updatedAt(), x.finishedAt(), x.errorCode())).toList(),
                page.total(), page.page(), page.size());
    }

    @Override
    public CancelResult cancel(ToolActorContext context, CancelCommand command) {
        var result = commandService.cancel(context.userId(), command.taskId());
        return new CancelResult(result.taskId(), result.status(), result.updatedAt());
    }
}
