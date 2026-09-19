package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SupplierCreateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.SupplierDraft, FinanceToolPort.SupplierDraftResult> {
    private final FinanceToolPort port;

    public SupplierCreateDraftToolHandler(FinanceToolPort port, ObjectMapper mapper) {
        super(ToolCode.SUPPLIER_CREATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected FinanceToolPort.SupplierDraft parseArguments(JsonNode arguments) {
        return SupplierDraftArguments.parse(arguments);
    }

    @Override protected FinanceToolPort.SupplierDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.SupplierDraft command) {
        return port.createSupplierDraft(context.actor(), command);
    }
}
