package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class PageActionQueryToolHandler extends TypedReadToolHandler<PlatformOperationsToolPort.PageActionQuery, PlatformOperationsToolPort.Page<PlatformOperationsToolPort.PageAction>> {
    private final PlatformOperationsToolPort port;
    public PageActionQueryToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) { super(ToolCode.PAGE_ACTION_QUERY, mapper); this.port = port; }
    @Override protected PlatformOperationsToolPort.PageActionQuery parseArguments(JsonNode a) { return new PlatformOperationsToolPort.PageActionQuery(optionalText(a, "targetPageId"), optionalBoolean(a, "enabled"), pageNumber(a), pageSize(a)); }
    @Override protected PlatformOperationsToolPort.Page<PlatformOperationsToolPort.PageAction> invoke(TrustedToolContext c, PlatformOperationsToolPort.PageActionQuery q) { return port.pageActions(c.actor(), q); }
}
