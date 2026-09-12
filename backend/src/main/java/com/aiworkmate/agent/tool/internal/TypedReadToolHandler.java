package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fixed execution skeleton for bounded read-only tools. Subclasses keep typed
 * arguments and domain-specific ports; this template only owns safe ordering
 * and output normalization.
 */
abstract class TypedReadToolHandler<Q, R> implements ToolHandler {
    private static final String DEFAULT_HANDLER_VERSION = "1.0.0";

    private final ToolCode code;
    private final ObjectMapper objectMapper;

    protected TypedReadToolHandler(ToolCode code, ObjectMapper objectMapper) {
        if (code == null || objectMapper == null) {
            throw new IllegalArgumentException("Read tool code and object mapper are required");
        }
        this.code = code;
        this.objectMapper = objectMapper;
    }

    @Override
    public final String toolCode() {
        return code.code();
    }

    @Override
    public String handlerVersion() {
        return DEFAULT_HANDLER_VERSION;
    }

    @Override
    public final JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        Q query = parseArguments(arguments);
        R result = invoke(context, query);
        return serializeResult(result);
    }

    protected abstract Q parseArguments(JsonNode arguments);

    protected abstract R invoke(TrustedToolContext context, Q query);

    protected JsonNode serializeResult(R result) {
        return ToolJsonOutput.omitNulls(objectMapper.valueToTree(result));
    }

    protected final ObjectMapper objectMapper() {
        return objectMapper;
    }
}
