package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalFormDefinitionQueryToolHandler
        extends TypedReadToolHandler<Long, ApprovalConfigurationToolPort.FormDefinition> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalFormDefinitionQueryToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_FORM_DEFINITION_QUERY, mapper);
        this.port = port;
    }
    @Override protected Long parseArguments(JsonNode arguments) {
        return BoundedToolArguments.requiredLong(arguments, "formId", 1);
    }
    @Override protected ApprovalConfigurationToolPort.FormDefinition invoke(TrustedToolContext context, Long formId) {
        return port.readFormDefinition(context.actor(), formId);
    }
}
