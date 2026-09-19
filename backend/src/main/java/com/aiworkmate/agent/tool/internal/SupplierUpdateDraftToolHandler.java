package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SupplierUpdateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.SupplierDraftUpdate, FinanceToolPort.SupplierDraftResult> {
    private final FinanceToolPort port;

    public SupplierUpdateDraftToolHandler(FinanceToolPort port, ObjectMapper mapper) {
        super(ToolCode.SUPPLIER_UPDATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected FinanceToolPort.SupplierDraftUpdate parseArguments(JsonNode arguments) {
        return SupplierDraftArguments.parseUpdate(arguments);
    }

    @Override protected FinanceToolPort.SupplierDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.SupplierDraftUpdate command) {
        return port.updateSupplierDraft(context.actor(), command);
    }
}
