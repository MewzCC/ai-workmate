package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class KnowledgeDocumentReindexToolHandler extends TypedWriteToolHandler<
        KnowledgeToolPort.ReindexDocumentCommand, KnowledgeToolPort.ReindexDocumentResult> {
    private final KnowledgeToolPort port;

    public KnowledgeDocumentReindexToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_DOCUMENT_REINDEX, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.ReindexDocumentCommand parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.ReindexDocumentCommand(requiredLong(arguments, "documentId", 1));
    }

    @Override
    protected KnowledgeToolPort.ReindexDocumentResult invoke(
            TrustedToolContext context, KnowledgeToolPort.ReindexDocumentCommand command) {
        return port.reindexDocument(context.actor(), command);
    }
}
