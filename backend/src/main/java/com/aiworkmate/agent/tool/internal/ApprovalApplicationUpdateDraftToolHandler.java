package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class ApprovalApplicationUpdateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalApplicationUpdateDraftToolHandler.Command, ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort port;

    public ApprovalApplicationUpdateDraftToolHandler(
            ApprovalApplicationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected Command parseArguments(JsonNode arguments) {
        return new Command(requiredLong(arguments, "applicationId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                new ApprovalApplicationToolPort.DraftUpdate(optionalText(arguments, "processKey"),
                        ApprovalApplicationDraftArguments.parseFields(arguments)));
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invoke(
            TrustedToolContext context, Command command) {
        return port.updateDraft(
                context.actor(), command.applicationId(), command.version(), command.update());
    }

    record Command(long applicationId, int version, ApprovalApplicationToolPort.DraftUpdate update) { }
}
