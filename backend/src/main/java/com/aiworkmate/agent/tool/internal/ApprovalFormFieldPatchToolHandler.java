package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ApprovalFormFieldPatchToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.FormFieldPatch, ApprovalConfigurationToolPort.FormFieldPatchResult> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalFormFieldPatchToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_FORM_PATCH_DRAFT_FIELD, mapper); this.port = port;
    }
    @Override protected ApprovalConfigurationToolPort.FormFieldPatch parseArguments(JsonNode args) {
        String label = text(args, "label", 40), placeholder = text(args, "placeholder", 80), width = text(args, "width", 4);
        if (args.has("required") && !args.path("required").isBoolean()
                || label == null && placeholder == null && width == null && !args.has("required")) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new ApprovalConfigurationToolPort.FormFieldPatch(requiredLong(args, "formId", 1),
                requiredInt(args, "version", 1, Integer.MAX_VALUE - 1), requiredText(args, "fieldName"),
                label, optionalBoolean(args, "required"), placeholder, width);
    }
    private String text(JsonNode args, String name, int maximum) {
        JsonNode value = args.get(name);
        if (value == null) return null;
        if (!value.isTextual() || value.textValue().length() > maximum) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return value.textValue();
    }
    @Override protected ApprovalConfigurationToolPort.FormFieldPatchResult invoke(TrustedToolContext context,
            ApprovalConfigurationToolPort.FormFieldPatch command) {
        return port.patchFormDraftField(context.actor(), command);
    }
}
