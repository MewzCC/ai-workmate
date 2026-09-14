package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class VisitorQueryToolHandler
        extends TypedReadToolHandler<VisitorToolPort.Query, VisitorToolPort.Page> {
    private final VisitorToolPort port;

    public VisitorQueryToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected VisitorToolPort.Query parseArguments(JsonNode arguments) {
        Long id = optionalPositiveLong(arguments, "bookingId");
        String queue = optionalText(arguments, "queue");
        return new VisitorToolPort.Query(id,
                queue == null ? VisitorToolPort.Queue.MINE : VisitorToolPort.Queue.valueOf(queue),
                optionalText(arguments, "status"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50));
    }

    @Override protected VisitorToolPort.Page invoke(TrustedToolContext context, VisitorToolPort.Query query) {
        return port.query(context.actor(), query);
    }
}
