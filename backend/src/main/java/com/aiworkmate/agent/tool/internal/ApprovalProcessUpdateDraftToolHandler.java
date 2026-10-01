package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ApprovalProcessUpdateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.ProcessDraftUpdate, ApprovalConfigurationToolPort.ProcessDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalProcessUpdateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_PROCESS_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.ProcessDraftUpdate parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.ProcessDraftUpdate(
                requiredLong(arguments, "processId", 1), requiredInt(arguments, "version", 1, Integer.MAX_VALUE),
                requiredText(arguments, "processName"), optionalTextPreservingEmpty(arguments, "description"),
                optionalPositiveLong(arguments, "formId"), ApprovalProcessDraftArguments.nodes(arguments));
    }

    @Override
    protected ApprovalConfigurationToolPort.ProcessDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.ProcessDraftUpdate command) {
        return port.updateProcessDraft(context.actor(), command);
    }
}
