package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class SupplierQueryToolHandler extends TypedReadToolHandler<FinanceToolPort.SupplierQuery, FinanceToolPort.Page<FinanceToolPort.Supplier>> {
    private final FinanceToolPort port;
    public SupplierQueryToolHandler(FinanceToolPort port, ObjectMapper mapper) { super(ToolCode.SUPPLIER_QUERY, mapper); this.port = port; }
    @Override protected FinanceToolPort.SupplierQuery parseArguments(JsonNode a) {
        return new FinanceToolPort.SupplierQuery(optionalPositiveLong(a, "supplierId"), optionalText(a, "keyword"), optionalText(a, "status"),
                optionalText(a, "category"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50));
    }
    @Override protected FinanceToolPort.Page<FinanceToolPort.Supplier> invoke(TrustedToolContext c, FinanceToolPort.SupplierQuery q) { return port.suppliers(c.actor(), q); }
}
