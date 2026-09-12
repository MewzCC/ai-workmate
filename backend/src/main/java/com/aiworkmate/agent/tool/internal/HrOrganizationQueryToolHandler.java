package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class HrOrganizationQueryToolHandler
        extends TypedReadToolHandler<HrOrganizationToolPort.Query, HrOrganizationToolPort.Result> {
    private final HrOrganizationToolPort port;

    public HrOrganizationQueryToolHandler(HrOrganizationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_ORGANIZATION_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected HrOrganizationToolPort.Query parseArguments(JsonNode arguments) {
        return new HrOrganizationToolPort.Query(
                optionalText(arguments, "keyword"), positiveInt(arguments, "limit", 50, 50));
    }

    @Override protected HrOrganizationToolPort.Result invoke(
            TrustedToolContext context, HrOrganizationToolPort.Query query) {
        return port.query(context.actor(), query);
    }

}
