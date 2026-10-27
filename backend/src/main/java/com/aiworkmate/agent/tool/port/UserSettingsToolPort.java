package com.aiworkmate.agent.tool.port;

/** Transport-neutral personal settings contract for local or future remote domain adapters. */
public interface UserSettingsToolPort {
    UpdateResult update(ToolActorContext context, UpdateCommand command);

    record UpdateCommand(String model, int maxContextRounds, boolean stream,
                         boolean forcePdfOcr) { }

    record UpdateResult(String model, int maxContextRounds, boolean stream,
                        boolean forcePdfOcr) implements ToolWriteReceipt { }
}
