package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DocumentMetadataToolModels;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class DocumentMetadataQueryToolHandler extends TypedReadToolHandler<DocumentMetadataToolModels.Query,
        ToolPage<DocumentMetadataToolModels.Item>> {
    private final String resourceArgument;
    protected DocumentMetadataQueryToolHandler(ToolCode code, String resourceArgument, ObjectMapper json) {
        super(code, json);
        this.resourceArgument = resourceArgument;
    }
    @Override protected DocumentMetadataToolModels.Query parseArguments(JsonNode arguments) {
        Integer page = BoundedToolArguments.optionalInt(arguments, "page", 1, 10000);
        Integer size = BoundedToolArguments.optionalInt(arguments, "size", 1, 20);
        return new DocumentMetadataToolModels.Query(BoundedToolArguments.requiredLong(arguments, resourceArgument, 1),
                page == null ? 1 : page, size == null ? 20 : size);
    }
}
