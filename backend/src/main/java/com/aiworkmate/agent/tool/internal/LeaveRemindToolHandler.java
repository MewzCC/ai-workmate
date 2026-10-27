package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class LeaveRemindToolHandler extends TypedVersionedWriteToolHandler<LeaveToolPort.StatusResult> {
    private final LeaveToolPort port;

    public LeaveRemindToolHandler(LeaveToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_REMIND, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected LeaveToolPort.StatusResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.remind(context.actor(), applicationId, version);
    }
}
