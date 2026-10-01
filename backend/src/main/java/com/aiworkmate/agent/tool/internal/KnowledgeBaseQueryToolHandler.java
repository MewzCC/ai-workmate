package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class KnowledgeBaseQueryToolHandler extends TypedReadToolHandler<
        KnowledgeToolPort.BaseQuery, KnowledgeToolPort.BaseQueryResult> {
    private final KnowledgeToolPort port;

    public KnowledgeBaseQueryToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_BASE_QUERY, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.BaseQuery parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.BaseQuery(optionalPositiveLong(arguments, "kbId"),
                positiveInt(arguments, "limit", 20, 50));
    }

    @Override
    protected KnowledgeToolPort.BaseQueryResult invoke(
            TrustedToolContext context, KnowledgeToolPort.BaseQuery query) {
        return port.queryBases(context.actor(), query);
    }
}
