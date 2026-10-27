package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class KnowledgeDocumentQueryToolHandler extends TypedReadToolHandler<
        KnowledgeToolPort.DocumentQuery, KnowledgeToolPort.DocumentQueryResult> {
    private final KnowledgeToolPort port;

    public KnowledgeDocumentQueryToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_DOCUMENT_QUERY, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.DocumentQuery parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.DocumentQuery(requiredLong(arguments, "kbId", 1),
                optionalPositiveLong(arguments, "documentId"), pageNumber(arguments),
                positiveInt(arguments, "size", 20, 20));
    }

    @Override
    protected KnowledgeToolPort.DocumentQueryResult invoke(
            TrustedToolContext context, KnowledgeToolPort.DocumentQuery query) {
        return port.queryDocuments(context.actor(), query);
    }
}
