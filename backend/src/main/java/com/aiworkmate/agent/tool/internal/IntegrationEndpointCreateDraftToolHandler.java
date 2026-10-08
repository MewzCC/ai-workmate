package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class IntegrationEndpointCreateDraftToolHandler extends TypedWriteToolHandler<
        PlatformOperationsToolPort.CreateEndpointCommand,
        PlatformOperationsToolPort.CreateEndpointResult> {
    private final PlatformOperationsToolPort port;

    public IntegrationEndpointCreateDraftToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) {
        super(ToolCode.INTEGRATION_ENDPOINT_CREATE_DRAFT, mapper);
        this.port = port;
    }

    @Override
    protected PlatformOperationsToolPort.CreateEndpointCommand parseArguments(JsonNode arguments) {
        return new PlatformOperationsToolPort.CreateEndpointCommand(
                requiredText(arguments, "code"), requiredText(arguments, "name"),
                requiredText(arguments, "upstreamCode"), requiredText(arguments, "method"),
                requiredText(arguments, "relativePath"), optionalText(arguments, "requestTemplate"),
                optionalText(arguments, "description"));
    }

    @Override
    protected PlatformOperationsToolPort.CreateEndpointResult invoke(
            TrustedToolContext context, PlatformOperationsToolPort.CreateEndpointCommand command) {
        return port.createEndpointDraft(context.actor(), command);
    }
}
