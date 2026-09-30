package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ContractRemindToolHandler
        extends TypedVersionedWriteToolHandler<ContractToolPort.ContractReminderResult> {
    private final ContractToolPort port;

    public ContractRemindToolHandler(ContractToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.CONTRACT_REMIND, objectMapper, "contractId");
        this.port = port;
    }

    @Override
    protected ContractToolPort.ContractReminderResult invokeVersioned(
            TrustedToolContext context, long contractId, int version) {
        return port.remindContract(context.actor(), contractId, version);
    }
}
