package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class IntegrationEndpointUpdateDraftToolHandler extends TypedWriteToolHandler<
        PlatformOperationsToolPort.UpdateEndpointCommand,
        PlatformOperationsToolPort.CreateEndpointResult> {
    private final PlatformOperationsToolPort port;

    public IntegrationEndpointUpdateDraftToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) {
        super(ToolCode.INTEGRATION_ENDPOINT_UPDATE_DRAFT, mapper);
        this.port = port;
    }

    @Override
    protected PlatformOperationsToolPort.UpdateEndpointCommand parseArguments(JsonNode arguments) {
        return new PlatformOperationsToolPort.UpdateEndpointCommand(
                requiredLong(arguments, "endpointId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "code"), requiredText(arguments, "name"),
                requiredText(arguments, "upstreamCode"), requiredText(arguments, "method"),
                requiredText(arguments, "relativePath"), optionalText(arguments, "requestTemplate"),
                optionalText(arguments, "description"));
    }

    @Override
    protected PlatformOperationsToolPort.CreateEndpointResult invoke(
            TrustedToolContext context, PlatformOperationsToolPort.UpdateEndpointCommand command) {
        return port.updateEndpointDraft(context.actor(), command);
    }
}
