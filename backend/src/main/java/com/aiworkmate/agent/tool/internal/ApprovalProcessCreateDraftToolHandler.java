package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ApprovalProcessCreateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.ProcessDraft, ApprovalConfigurationToolPort.ProcessDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalProcessCreateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_PROCESS_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.ProcessDraft parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.ProcessDraft(requiredText(arguments, "processKey"),
                requiredText(arguments, "processName"), optionalTextPreservingEmpty(arguments, "description"),
                optionalPositiveLong(arguments, "formId"), ApprovalProcessDraftArguments.nodes(arguments));
    }

    @Override
    protected ApprovalConfigurationToolPort.ProcessDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.ProcessDraft command) {
        return port.createProcessDraft(context.actor(), command);
    }
}
