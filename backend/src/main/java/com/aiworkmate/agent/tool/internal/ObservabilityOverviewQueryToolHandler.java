package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public final class ObservabilityOverviewQueryToolHandler
        extends TypedReadToolHandler<ObservabilityToolPort.OverviewQuery, ObservabilityToolPort.OverviewResult> {
    private final ObservabilityToolPort port;
    public ObservabilityOverviewQueryToolHandler(ObservabilityToolPort port, ObjectMapper mapper) {
        super(ToolCode.OBSERVABILITY_OVERVIEW_QUERY, mapper);
        this.port = port;
    }
    @Override protected ObservabilityToolPort.OverviewQuery parseArguments(JsonNode arguments) {
        String range = arguments.has("range") ? BoundedToolArguments.requiredText(arguments, "range") : "7d";
        if (!Set.of("24h", "7d", "30d").contains(range)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return new ObservabilityToolPort.OverviewQuery(range);
    }
    @Override protected ObservabilityToolPort.OverviewResult invoke(TrustedToolContext context,
                                                                    ObservabilityToolPort.OverviewQuery query) {
        return port.overview(context.actor(), query);
    }
}
