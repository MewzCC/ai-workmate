package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolWriteReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Template for monotonic state-setting writes whose repeated execution returns
 * the same business state without creating another business operation.
 */
abstract class TypedNaturallyIdempotentWriteToolHandler<C, R extends ToolWriteReceipt>
        extends TypedWriteToolHandler<C, R> {
    protected TypedNaturallyIdempotentWriteToolHandler(ToolCode code, ObjectMapper objectMapper) {
        super(code, objectMapper, ToolExecutionTemplate.NATURALLY_IDEMPOTENT_WRITE);
    }
}
