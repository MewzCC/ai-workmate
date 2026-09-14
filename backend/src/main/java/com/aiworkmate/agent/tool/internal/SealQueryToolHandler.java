package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class SealQueryToolHandler extends TypedReadToolHandler<SealToolPort.Query, SealToolPort.Page> {
    private final SealToolPort port;

    public SealQueryToolHandler(SealToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.SEAL_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected SealToolPort.Query parseArguments(JsonNode arguments) {
        Long id = optionalPositiveLong(arguments, "usageId");
        String queue = optionalText(arguments, "queue");
        return new SealToolPort.Query(id,
                queue == null ? SealToolPort.Queue.MINE : SealToolPort.Queue.valueOf(queue),
                optionalText(arguments, "status"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50));
    }

    @Override protected SealToolPort.Page invoke(TrustedToolContext context, SealToolPort.Query query) {
        return port.query(context.actor(), query);
    }
}
