package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class KnowledgeDocumentCreateTextToolHandler extends TypedWriteToolHandler<
        KnowledgeToolPort.CreateTextCommand, KnowledgeToolPort.CreateTextResult> {
    private final KnowledgeToolPort port;

    public KnowledgeDocumentCreateTextToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_DOCUMENT_CREATE_TEXT, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.CreateTextCommand parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.CreateTextCommand(requiredLong(arguments, "kbId", 1),
                requiredText(arguments, "filename"), requiredText(arguments, "content"));
    }

    @Override
    protected KnowledgeToolPort.CreateTextResult invoke(
            TrustedToolContext context, KnowledgeToolPort.CreateTextCommand command) {
        return port.createText(context.actor(), command);
    }
}
