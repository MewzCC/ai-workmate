package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationTemplateQueryToolHandler
        extends TypedReadToolHandler<ApprovalConfigurationToolPort.TemplateQuery, ApprovalConfigurationToolPort.Templates> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalApplicationTemplateQueryToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_APPLICATION_TEMPLATE_QUERY, mapper);
        this.port = port;
    }
    @Override protected ApprovalConfigurationToolPort.TemplateQuery parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.TemplateQuery(BoundedToolArguments.optionalText(arguments, "keyword"),
                BoundedToolArguments.optionalText(arguments, "formKey"), BoundedToolArguments.pageNumber(arguments, 10000),
                Math.min(20, BoundedToolArguments.pageSize(arguments)));
    }
    @Override protected ApprovalConfigurationToolPort.Templates invoke(TrustedToolContext context, ApprovalConfigurationToolPort.TemplateQuery query) {
        return port.applicationTemplates(context.actor(), query);
    }
    @Override protected JsonNode serializeResult(ApprovalConfigurationToolPort.Templates result) {
        JsonNode output = super.serializeResult(result);
        for (JsonNode item : output.path("items")) ApprovalFormToolJsonOutput.omitEmptyColumns(item.path("fields"));
        return output;
    }
}
