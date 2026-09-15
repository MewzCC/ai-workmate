package com.aiworkmate.agent.registry;

import java.util.Optional;

/** Internal resolution result, not an execution permit or public policy diagnostics. */
public record ToolAvailability(Status status, Optional<ToolDefinition> definition) {
    public enum Status { AVAILABLE, DISABLED, UNAVAILABLE }

    public ToolAvailability {
        if (status == null || definition == null
                || (status == Status.AVAILABLE) != definition.isPresent()) {
            throw new IllegalArgumentException("Invalid tool availability result");
        }
    }

    public static ToolAvailability available(ToolDefinition definition) {
        return new ToolAvailability(Status.AVAILABLE, Optional.of(definition));
    }

    public static ToolAvailability unavailable(Status status) {
        return new ToolAvailability(status, Optional.empty());
    }
}
