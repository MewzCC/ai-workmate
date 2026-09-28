package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Write template for commands whose domain boundary requires a stable operation key.
 * The key always comes from trusted task coordinates and can never be parsed from model input.
 */
abstract class TypedOperationKeyWriteToolHandler<C, R extends ToolWriteReceipt>
        extends TypedWriteToolHandler<C, R> {
    protected TypedOperationKeyWriteToolHandler(ToolCode code, ObjectMapper objectMapper) {
        super(code, objectMapper, ToolExecutionTemplate.OPERATION_KEY_WRITE);
    }

    @Override
    protected final R invoke(TrustedToolContext context, C command) {
        return invokeWithOperationKey(context, command, StableToolOperationKey.v1(context, code()));
    }

    protected abstract R invokeWithOperationKey(
            TrustedToolContext context, C command, ToolOperationKey operationKey);

    private ToolCode code() {
        return ToolCode.fromCode(toolCode());
    }
}
