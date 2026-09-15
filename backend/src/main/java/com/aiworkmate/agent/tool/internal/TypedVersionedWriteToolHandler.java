package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

/** Shared command shape for a single optimistic-lock protected resource write. */
abstract class TypedVersionedWriteToolHandler<R>
        extends TypedWriteToolHandler<TypedVersionedWriteToolHandler.Command, R> {
    private final String idArgument;

    protected TypedVersionedWriteToolHandler(
            ToolCode code, ObjectMapper objectMapper, String idArgument) {
        super(code, objectMapper);
        if (idArgument == null || idArgument.isBlank()) {
            throw new IllegalArgumentException("Resource id argument is required");
        }
        this.idArgument = idArgument;
    }

    @Override
    protected final Command parseArguments(JsonNode arguments) {
        return new Command(requiredLong(arguments, idArgument, 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1));
    }

    @Override
    protected final R invoke(TrustedToolContext context, Command command) {
        return invokeVersioned(context, command.resourceId(), command.version());
    }

    protected abstract R invokeVersioned(TrustedToolContext context, long resourceId, int version);

    record Command(long resourceId, int version) { }
}
