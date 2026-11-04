package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationApplyToolHandler
        extends TypedOperationKeyWriteToolHandler<ApprovalApplicationToolPort.Draft, ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort port;
    public ApprovalApplicationApplyToolHandler(ApprovalApplicationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_APPLICATION_APPLY, mapper);
        this.port = port;
    }
    @Override protected ApprovalApplicationToolPort.Draft parseArguments(JsonNode arguments) {
        return new ApprovalApplicationToolPort.Draft(BoundedToolArguments.requiredText(arguments, "formKey"),
                BoundedToolArguments.optionalText(arguments, "processKey"), ApprovalApplicationDraftArguments.parseFields(arguments));
    }
    @Override protected ApprovalApplicationToolPort.WriteResult invokeWithOperationKey(TrustedToolContext context,
            ApprovalApplicationToolPort.Draft command, ToolOperationKey key) {
        return port.apply(context.actor(), command, key);
    }
}
