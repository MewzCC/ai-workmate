package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AgentTaskQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentTaskCenterAgentDomainToolAdapter implements AgentTaskCenterToolPort {
    private final AgentTaskQueryService service;

    @Override
    public TaskPage mine(ToolActorContext context, TaskQuery query) {
        var page = service.mine(context.userId(), query.status(), query.from(), query.to(), query.page(), query.size());
        return new TaskPage(page.records().stream().map(x -> new Task(x.taskId(), x.pageId(), x.status(),
                x.riskLevel(), x.planVersion(), x.createdAt(), x.updatedAt(), x.finishedAt(), x.errorCode())).toList(),
                page.total(), page.page(), page.size());
    }
}
