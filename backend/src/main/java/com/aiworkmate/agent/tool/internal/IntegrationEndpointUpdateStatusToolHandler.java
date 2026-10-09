package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class IntegrationEndpointUpdateStatusToolHandler extends TypedWriteToolHandler<
        PlatformOperationsToolPort.EndpointStatusCommand,
        PlatformOperationsToolPort.EndpointStatusResult> {
    private final PlatformOperationsToolPort port;

    public IntegrationEndpointUpdateStatusToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) {
        super(ToolCode.INTEGRATION_ENDPOINT_UPDATE_STATUS, mapper);
        this.port = port;
    }

    @Override
    protected PlatformOperationsToolPort.EndpointStatusCommand parseArguments(JsonNode arguments) {
        return new PlatformOperationsToolPort.EndpointStatusCommand(
                requiredLong(arguments, "endpointId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"));
    }

    @Override
    protected PlatformOperationsToolPort.EndpointStatusResult invoke(
            TrustedToolContext context, PlatformOperationsToolPort.EndpointStatusCommand command) {
        return port.updateEndpointStatus(context.actor(), command);
    }
}
