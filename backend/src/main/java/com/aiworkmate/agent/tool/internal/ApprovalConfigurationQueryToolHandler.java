package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;

@Component
public final class ApprovalConfigurationQueryToolHandler
        extends TypedReadToolHandler<ApprovalConfigurationToolPort.Query, ApprovalConfigurationToolPort.Page> {
    private static final int MAX_SIZE = 50;

    private final ApprovalConfigurationToolPort approvalConfigurationToolPort;

    public ApprovalConfigurationQueryToolHandler(
            ApprovalConfigurationToolPort approvalConfigurationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_CONFIGURATION_QUERY, objectMapper);
        this.approvalConfigurationToolPort = approvalConfigurationToolPort;
    }

    @Override protected ApprovalConfigurationToolPort.Query parseArguments(JsonNode arguments) {
        ApprovalConfigurationToolPort.Resource resource = requiredEnum(
                arguments, "resource", ApprovalConfigurationToolPort.Resource.class);
        int page = positiveInt(arguments, "page", 1, Integer.MAX_VALUE);
        int size = positiveInt(arguments, "size", 20, MAX_SIZE);
        return new ApprovalConfigurationToolPort.Query(resource, optionalText(arguments, "keyword"),
                optionalText(arguments, "status"), page, size);
    }

    @Override protected ApprovalConfigurationToolPort.Page invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.Query query) {
        return approvalConfigurationToolPort.query(context.userId(), query);
    }

}
