package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageSize;

@Component
public final class AssetQueryToolHandler extends TypedReadToolHandler<AssetToolPort.Query, AssetToolPort.Page> {
    private final AssetToolPort port;

    public AssetQueryToolHandler(AssetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ASSET_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected AssetToolPort.Query parseArguments(JsonNode arguments) {
        return new AssetToolPort.Query(
                optionalText(arguments, "keyword"), optionalText(arguments, "category"),
                optionalText(arguments, "status"), pageNumber(arguments),
                pageSize(arguments));
    }

    @Override protected AssetToolPort.Page invoke(TrustedToolContext context, AssetToolPort.Query query) {
        return port.query(context.actor(), query);
    }
}
