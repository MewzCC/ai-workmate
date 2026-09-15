package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalApplicationCreateDraftToolHandler extends TypedWriteToolHandler<ApprovalApplicationToolPort.Draft, ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort approvalApplicationToolPort;

    public ApprovalApplicationCreateDraftToolHandler(
            ApprovalApplicationToolPort approvalApplicationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT, objectMapper);
        this.approvalApplicationToolPort = approvalApplicationToolPort;
    }

    @Override
    protected ApprovalApplicationToolPort.Draft parseArguments(JsonNode arguments) {
        JsonNode fieldsNode = arguments.path("fields");
        if (!fieldsNode.isArray() || fieldsNode.size() > 100) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        List<ApprovalApplicationToolPort.FieldValue> fields = new ArrayList<>(fieldsNode.size());
        for (JsonNode field : fieldsNode) {
            String name = requiredText(field, "name");
            JsonNode single = field.get("value");
            JsonNode multiple = field.get("values");
            if ((single == null) == (multiple == null)) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            if (single != null) {
                if (!single.isTextual()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
                fields.add(new ApprovalApplicationToolPort.FieldValue(
                        name, List.of(single.asText()), false));
            } else {
                if (!multiple.isArray() || multiple.size() > 20) {
                    throw new BusinessException(ErrorCode.REQUEST_INVALID);
                }
                List<String> values = new ArrayList<>(multiple.size());
                multiple.forEach(value -> {
                    if (!value.isTextual()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
                    values.add(value.asText());
                });
                fields.add(new ApprovalApplicationToolPort.FieldValue(name, values, true));
            }
        }
        return new ApprovalApplicationToolPort.Draft(requiredText(arguments, "formKey"),
                optionalText(arguments, "processKey"), fields);
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invoke(
            TrustedToolContext context, ApprovalApplicationToolPort.Draft command) {
        return approvalApplicationToolPort.createDraft(
                context.actor(), command, stableOperationKey(context));
    }
}
