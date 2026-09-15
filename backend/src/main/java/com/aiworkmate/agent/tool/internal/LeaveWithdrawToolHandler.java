package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class LeaveWithdrawToolHandler extends TypedVersionedWriteToolHandler<LeaveToolPort.WithdrawalResult> {
    private final LeaveToolPort port;

    public LeaveWithdrawToolHandler(LeaveToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_WITHDRAW, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected LeaveToolPort.WithdrawalResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.withdraw(context.actor(), applicationId, version);
    }
}
