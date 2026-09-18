package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fixed execution skeleton for one bounded write command. The template owns
 * handler identity, parse/invoke/serialize ordering and stable operation-key
 * generation. Domain authorization, ownership, state and optimistic locking
 * remain in the typed port and its domain service.
 */
abstract class TypedWriteToolHandler<C, R> implements ToolHandler {
    private static final String DEFAULT_HANDLER_VERSION = "1.0.0";

    private final ToolCode code;
    private final ObjectMapper objectMapper;

    protected TypedWriteToolHandler(ToolCode code, ObjectMapper objectMapper) {
        if (code == null || objectMapper == null) {
            throw new IllegalArgumentException("Write tool code and object mapper are required");
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
        C command = parseArguments(arguments);
        R result = invoke(context, command);
        return serializeResult(result);
    }

    protected abstract C parseArguments(JsonNode arguments);

    protected abstract R invoke(TrustedToolContext context, C command);

    protected JsonNode serializeResult(R result) {
        return objectMapper.valueToTree(result);
    }

    protected final ToolOperationKey stableOperationKey(TrustedToolContext context) {
        return StableToolOperationKey.v1(context, code);
    }

    protected final ObjectMapper objectMapper() {
        return objectMapper;
    }
}
