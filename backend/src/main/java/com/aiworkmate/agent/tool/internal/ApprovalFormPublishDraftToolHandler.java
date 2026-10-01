package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class ApprovalFormPublishDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.VersionedForm, ApprovalConfigurationToolPort.FormDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalFormPublishDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_FORM_PUBLISH_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.VersionedForm parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.VersionedForm(
                requiredLong(arguments, "formId", 1),
                requiredInt(arguments, "version", 1, Integer.MAX_VALUE));
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.VersionedForm command) {
        return port.publishFormDraft(context.actor(), command);
    }
}
