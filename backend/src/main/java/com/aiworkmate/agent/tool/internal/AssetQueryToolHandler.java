package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class AssetQueryToolHandler implements ToolHandler {
    private final AssetToolPort port;
    private final ObjectMapper objectMapper;
    @Override public String toolCode() { return ToolCode.ASSET_QUERY.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var result = port.query(context.userId(), new AssetToolPort.Query(
                optionalText(arguments, "keyword"), optionalText(arguments, "category"),
                optionalText(arguments, "status"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50)));
        return ToolJsonOutput.omitNulls(objectMapper.valueToTree(result));
    }
}
