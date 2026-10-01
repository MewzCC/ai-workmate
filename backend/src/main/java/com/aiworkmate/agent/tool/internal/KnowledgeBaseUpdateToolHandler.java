package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class KnowledgeBaseUpdateToolHandler extends TypedWriteToolHandler<
        KnowledgeToolPort.UpdateBaseCommand, KnowledgeToolPort.UpdateBaseResult> {
    private final KnowledgeToolPort port;

    public KnowledgeBaseUpdateToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_BASE_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.UpdateBaseCommand parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.UpdateBaseCommand(requiredLong(arguments, "kbId", 1),
                optionalText(arguments, "name"), optionalTextPreservingEmpty(arguments, "icon"),
                optionalTextPreservingEmpty(arguments, "description"), optionalInt(arguments, "chunkSize", 100, 8000),
                optionalInt(arguments, "chunkOverlap", 0, 4000),
                optionalInt(arguments, "denseTopK", 1, 50), optionalInt(arguments, "sparseTopK", 0, 50));
    }

    @Override
    protected KnowledgeToolPort.UpdateBaseResult invoke(
            TrustedToolContext context, KnowledgeToolPort.UpdateBaseCommand command) {
        return port.updateBase(context.actor(), command);
    }
}
