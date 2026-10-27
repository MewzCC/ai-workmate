package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class KnowledgeBaseCreateToolHandler extends TypedWriteToolHandler<
        KnowledgeToolPort.CreateBaseCommand, KnowledgeToolPort.CreateBaseResult> {
    private final KnowledgeToolPort port;

    public KnowledgeBaseCreateToolHandler(KnowledgeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_BASE_CREATE, objectMapper);
        this.port = port;
    }

    @Override
    protected KnowledgeToolPort.CreateBaseCommand parseArguments(JsonNode arguments) {
        return new KnowledgeToolPort.CreateBaseCommand(requiredText(arguments, "name"),
                optionalText(arguments, "icon"), optionalText(arguments, "description"));
    }

    @Override
    protected KnowledgeToolPort.CreateBaseResult invoke(
            TrustedToolContext context, KnowledgeToolPort.CreateBaseCommand command) {
        return port.createBase(context.actor(), command);
    }
}
