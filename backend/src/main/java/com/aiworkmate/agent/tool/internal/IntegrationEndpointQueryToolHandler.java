package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class IntegrationEndpointQueryToolHandler extends TypedReadToolHandler<PlatformOperationsToolPort.EndpointQuery, PlatformOperationsToolPort.Page<PlatformOperationsToolPort.Endpoint>> {
    private final PlatformOperationsToolPort port;
    public IntegrationEndpointQueryToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) { super(ToolCode.INTEGRATION_ENDPOINT_QUERY, mapper); this.port = port; }
    @Override protected PlatformOperationsToolPort.EndpointQuery parseArguments(JsonNode a) { return new PlatformOperationsToolPort.EndpointQuery(optionalPositiveLong(a, "endpointId"), optionalText(a, "keyword"), optionalText(a, "status"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50)); }
    @Override protected PlatformOperationsToolPort.Page<PlatformOperationsToolPort.Endpoint> invoke(TrustedToolContext c, PlatformOperationsToolPort.EndpointQuery q) { return port.endpoints(c.actor(), q); }
}
