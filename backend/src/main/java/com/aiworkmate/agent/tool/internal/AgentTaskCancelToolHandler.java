package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class AgentTaskCancelToolHandler extends TypedWriteToolHandler<
        AgentTaskCenterToolPort.CancelCommand, AgentTaskCenterToolPort.CancelResult> {
    private final AgentTaskCenterToolPort port;

    public AgentTaskCancelToolHandler(AgentTaskCenterToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.AGENT_TASK_CANCEL, objectMapper);
        this.port = port;
    }

    @Override
    protected AgentTaskCenterToolPort.CancelCommand parseArguments(JsonNode arguments) {
        return new AgentTaskCenterToolPort.CancelCommand(requiredText(arguments, "taskId"));
    }

    @Override
    protected AgentTaskCenterToolPort.CancelResult invoke(
            TrustedToolContext context, AgentTaskCenterToolPort.CancelCommand command) {
        return port.cancel(context.actor(), command);
    }
}
