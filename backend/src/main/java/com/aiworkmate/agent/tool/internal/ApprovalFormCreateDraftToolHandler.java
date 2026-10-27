package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalFormCreateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.FormDraft, ApprovalConfigurationToolPort.FormDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalFormCreateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_FORM_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraft parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.FormDraft(
                requiredText(arguments, "formKey"), requiredText(arguments, "formName"),
                optionalTextPreservingEmpty(arguments, "description"),
                ApprovalFormDraftArguments.fields(arguments));
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.FormDraft command) {
        return port.createFormDraft(context.actor(), command);
    }
}
