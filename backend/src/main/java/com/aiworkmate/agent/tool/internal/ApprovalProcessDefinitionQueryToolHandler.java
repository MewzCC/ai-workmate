package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalProcessDefinitionQueryToolHandler
        extends TypedReadToolHandler<Long, ApprovalConfigurationToolPort.ProcessDefinition> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalProcessDefinitionQueryToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_PROCESS_DEFINITION_QUERY, mapper);
        this.port = port;
    }
    @Override protected Long parseArguments(JsonNode arguments) {
        return BoundedToolArguments.requiredLong(arguments, "processId", 1);
    }
    @Override protected ApprovalConfigurationToolPort.ProcessDefinition invoke(TrustedToolContext context, Long id) {
        return port.readProcessDefinition(context.actor(), id);
    }
}
