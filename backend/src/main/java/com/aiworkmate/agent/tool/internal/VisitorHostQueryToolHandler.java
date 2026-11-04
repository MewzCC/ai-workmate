package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BusinessUserOptionsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class VisitorHostQueryToolHandler extends TypedReadToolHandler<BusinessUserOptionsToolPort.Query, BusinessUserOptionsToolPort.Page> {
    private final BusinessUserOptionsToolPort port;
    public VisitorHostQueryToolHandler(BusinessUserOptionsToolPort port, ObjectMapper mapper) {
        super(ToolCode.VISITOR_HOST_QUERY, mapper); this.port = port;
    }
    @Override protected BusinessUserOptionsToolPort.Query parseArguments(JsonNode args) {
        return new BusinessUserOptionsToolPort.Query(BoundedToolArguments.optionalText(args, "keyword"),
                BoundedToolArguments.pageNumber(args), BoundedToolArguments.positiveInt(args, "size", 20, 20));
    }
    @Override protected BusinessUserOptionsToolPort.Page invoke(TrustedToolContext context, BusinessUserOptionsToolPort.Query query) {
        return port.visitorHosts(context.actor(), query);
    }
}
