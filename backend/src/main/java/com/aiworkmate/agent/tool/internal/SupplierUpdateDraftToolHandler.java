package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SupplierUpdateDraftToolHandler extends TypedWriteToolHandler<
        SupplierToolPort.SupplierDraftUpdate, SupplierToolPort.SupplierDraftResult> {
    private final SupplierToolPort port;

    public SupplierUpdateDraftToolHandler(SupplierToolPort port, ObjectMapper mapper) {
        super(ToolCode.SUPPLIER_UPDATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected SupplierToolPort.SupplierDraftUpdate parseArguments(JsonNode arguments) {
        return SupplierDraftArguments.parseUpdate(arguments);
    }

    @Override protected SupplierToolPort.SupplierDraftResult invoke(
            TrustedToolContext context, SupplierToolPort.SupplierDraftUpdate command) {
        return port.updateSupplierDraft(context.actor(), command);
    }
}
