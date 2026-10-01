package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalFormUpdateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.FormDraftUpdate, ApprovalConfigurationToolPort.FormDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalFormUpdateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_FORM_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraftUpdate parseArguments(JsonNode arguments) {
        long version = requiredLong(arguments, "version", 1);
        if (version > Integer.MAX_VALUE) throw invalid();
        return new ApprovalConfigurationToolPort.FormDraftUpdate(requiredLong(arguments, "formId", 1),
                (int) version, requiredText(arguments, "formName"),
                optionalTextPreservingEmpty(arguments, "description"),
                ApprovalFormDraftArguments.fields(arguments));
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.FormDraftUpdate command) {
        return port.updateFormDraft(context.actor(), command);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
