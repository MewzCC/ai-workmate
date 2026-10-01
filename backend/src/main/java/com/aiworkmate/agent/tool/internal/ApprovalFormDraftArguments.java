package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

/** Shared closed parser for semantic approval-form fields. */
final class ApprovalFormDraftArguments {
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "money", "date",
            "dateRange", "time", "radio", "checkbox", "select", "user", "department", "file",
            "image", "table", "divider");
    private static final Set<String> WIDTHS = Set.of("full", "half");

    private ApprovalFormDraftArguments() { }

    static List<ApprovalConfigurationToolPort.FormField> fields(JsonNode arguments) {
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
            fields.add(new ApprovalConfigurationToolPort.FormField(name, requiredText(field, "label"), type,
                    required.booleanValue(), optionalTextPreservingEmpty(field, "placeholder"),
                    options(field), width));
        }
        return List.copyOf(fields);
    }

    private static List<String> options(JsonNode field) {
        JsonNode values = field.get("options");
        if (values == null || values.isNull()) return List.of();
        if (!values.isArray() || values.size() > 10) throw invalid();
        List<String> result = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw invalid();
            result.add(value.asText().strip());
        }
        return List.copyOf(result);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
