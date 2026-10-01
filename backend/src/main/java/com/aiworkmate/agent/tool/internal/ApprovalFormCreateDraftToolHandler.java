package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalFormCreateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.FormDraft, ApprovalConfigurationToolPort.FormDraftResult> {
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "money", "date",
            "dateRange", "time", "radio", "checkbox", "select", "user", "department", "file",
            "image", "table", "divider");
    private static final Set<String> WIDTHS = Set.of("full", "half");
    private final ApprovalConfigurationToolPort port;

    public ApprovalFormCreateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_FORM_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraft parseArguments(JsonNode arguments) {
        JsonNode fieldsNode = arguments.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.isEmpty() || fieldsNode.size() > 20) {
            throw invalid();
        }
        List<ApprovalConfigurationToolPort.FormField> fields = new ArrayList<>(fieldsNode.size());
        Set<String> names = new HashSet<>();
        for (JsonNode field : fieldsNode) {
            if (!field.isObject()) throw invalid();
            String type = requiredText(field, "type");
            String width = requiredText(field, "width");
            JsonNode required = field.get("required");
            if (!TYPES.contains(type) || !WIDTHS.contains(width) || required == null || !required.isBoolean()) {
                throw invalid();
            }
            String name = requiredText(field, "name");
            if (!names.add(name)) throw invalid();
            fields.add(new ApprovalConfigurationToolPort.FormField(
                    name, requiredText(field, "label"), type,
                    required.booleanValue(), optionalTextPreservingEmpty(field, "placeholder"),
                    options(field), width));
        }
        return new ApprovalConfigurationToolPort.FormDraft(
                requiredText(arguments, "formKey"), requiredText(arguments, "formName"),
                optionalTextPreservingEmpty(arguments, "description"), fields);
    }

    private List<String> options(JsonNode field) {
        JsonNode values = field.get("options");
        if (values == null || values.isNull()) return List.of();
        if (!values.isArray() || values.size() > 10) throw invalid();
        List<String> result = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw invalid();
            result.add(value.asText().strip());
        }
        return result;
    }

    @Override
    protected ApprovalConfigurationToolPort.FormDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.FormDraft command) {
        return port.createFormDraft(context.actor(), command);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
