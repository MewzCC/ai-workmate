package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolWriteReceipt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fixed execution skeleton for one bounded write command. The template owns
 * handler identity, parse/invoke/serialize ordering and stable operation-key
 * generation. Domain authorization, ownership, state and optimistic locking
 * remain in the typed port and its domain service.
 */
abstract class TypedWriteToolHandler<C, R extends ToolWriteReceipt> implements ToolHandler {
    private final ToolCode code;
    private final ObjectMapper objectMapper;
    private final ToolExecutionTemplate executionTemplate;

    protected TypedWriteToolHandler(ToolCode code, ObjectMapper objectMapper) {
        this(code, objectMapper, ToolExecutionTemplate.DIRECT_WRITE);
    }

    protected TypedWriteToolHandler(
            ToolCode code, ObjectMapper objectMapper, ToolExecutionTemplate executionTemplate) {
        if (code == null || objectMapper == null) {
            throw new IllegalArgumentException("Write tool code and object mapper are required");
        }
        if (executionTemplate == null || !executionTemplate.isWrite()) {
            throw new IllegalArgumentException("Write execution template is required");
        }
        this.code = code;
        this.objectMapper = objectMapper;
        this.executionTemplate = executionTemplate;
    }

    @Override
    public final String toolCode() {
        return code.code();
    }

    @Override
    public String handlerVersion() {
        return ToolHandlerContract.VERSION;
    }

    @Override
    public final ToolExecutionTemplate executionTemplate() {
        return executionTemplate;
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

    protected final ObjectMapper objectMapper() {
        return objectMapper;
    }
}
