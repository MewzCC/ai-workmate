package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class RuntimeLogQueryToolHandler extends TypedReadToolHandler<PlatformOperationsToolPort.RuntimeLogQuery, PlatformOperationsToolPort.Page<PlatformOperationsToolPort.RuntimeLog>> {
    private final PlatformOperationsToolPort port;
    public RuntimeLogQueryToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) { super(ToolCode.RUNTIME_LOG_QUERY, mapper); this.port = port; }
    @Override protected PlatformOperationsToolPort.RuntimeLogQuery parseArguments(JsonNode a) { return new PlatformOperationsToolPort.RuntimeLogQuery(optionalText(a, "source"), optionalPositiveLong(a, "recordId"), optionalText(a, "outcome"), optionalText(a, "keyword"), optionalDateTime(a, "from"), optionalDateTime(a, "to"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50)); }
    @Override protected PlatformOperationsToolPort.Page<PlatformOperationsToolPort.RuntimeLog> invoke(TrustedToolContext c, PlatformOperationsToolPort.RuntimeLogQuery q) { return port.runtimeLogs(c.actor(), q); }
}
