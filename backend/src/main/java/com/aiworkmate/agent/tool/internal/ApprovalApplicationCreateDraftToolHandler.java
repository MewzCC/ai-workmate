package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalApplicationCreateDraftToolHandler extends TypedOperationKeyWriteToolHandler<ApprovalApplicationToolPort.Draft, ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort approvalApplicationToolPort;

    public ApprovalApplicationCreateDraftToolHandler(
            ApprovalApplicationToolPort approvalApplicationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT, objectMapper);
        this.approvalApplicationToolPort = approvalApplicationToolPort;
    }

    @Override
    protected ApprovalApplicationToolPort.Draft parseArguments(JsonNode arguments) {
        return new ApprovalApplicationToolPort.Draft(requiredText(arguments, "formKey"),
                optionalText(arguments, "processKey"), ApprovalApplicationDraftArguments.parseFields(arguments));
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invokeWithOperationKey(
            TrustedToolContext context, ApprovalApplicationToolPort.Draft command,
            ToolOperationKey operationKey) {
        return approvalApplicationToolPort.createDraft(
                context.actor(), command, operationKey);
    }
}
