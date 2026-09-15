package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class AgentTaskMineQueryToolHandler extends TypedReadToolHandler<AgentTaskCenterToolPort.TaskQuery, AgentTaskCenterToolPort.TaskPage> {
    private final AgentTaskCenterToolPort port;
    public AgentTaskMineQueryToolHandler(AgentTaskCenterToolPort port, ObjectMapper mapper) { super(ToolCode.AGENT_TASK_MINE_QUERY, mapper); this.port = port; }
    @Override protected AgentTaskCenterToolPort.TaskQuery parseArguments(JsonNode a) { return new AgentTaskCenterToolPort.TaskQuery(optionalText(a, "status"), optionalDateTime(a, "from"), optionalDateTime(a, "to"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50)); }
    @Override protected AgentTaskCenterToolPort.TaskPage invoke(TrustedToolContext c, AgentTaskCenterToolPort.TaskQuery q) { return port.mine(c.actor(), q); }
}
