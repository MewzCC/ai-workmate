package com.aiworkmate.agent.tool.internal;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolHandler {
    String toolCode();

    String handlerVersion();

    JsonNode execute(TrustedToolContext context, JsonNode arguments);
}
