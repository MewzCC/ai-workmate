package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class ApprovalProcessPublishDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.VersionedProcess, ApprovalConfigurationToolPort.ProcessDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalProcessPublishDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_PROCESS_PUBLISH_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.VersionedProcess parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.VersionedProcess(
                requiredLong(arguments, "processId", 1),
                requiredInt(arguments, "version", 1, Integer.MAX_VALUE));
    }

    @Override
    protected ApprovalConfigurationToolPort.ProcessDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.VersionedProcess command) {
        return port.publishProcessDraft(context.actor(), command);
    }
}
