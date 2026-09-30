package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class LeaveUpdateDraftToolHandler extends TypedWriteToolHandler<
        LeaveUpdateDraftToolHandler.Command, LeaveToolPort.WriteResult> {
    private final LeaveToolPort port;

    public LeaveUpdateDraftToolHandler(LeaveToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected Command parseArguments(JsonNode arguments) {
        return new Command(requiredLong(arguments, "applicationId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                LeaveDraftArguments.parse(arguments));
    }

    @Override
    protected LeaveToolPort.WriteResult invoke(TrustedToolContext context, Command command) {
        return port.updateDraft(context.actor(), command.applicationId(), command.version(), command.draft());
    }

    @Override
    protected JsonNode serializeResult(LeaveToolPort.WriteResult result) {
        ObjectNode output = objectMapper().createObjectNode();
        output.put("applicationId", result.applicationId());
        output.put("status", result.status());
        output.put("version", result.version());
        return output;
    }

    record Command(long applicationId, int version, LeaveToolPort.Draft draft) { }
}
