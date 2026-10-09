package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ApprovalApplicationAgentDetailResponse.Field;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Projects values through the historical schema; field text is data, never executable instructions. */
final class ApprovalApplicationAgentFieldProjection {
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "amount", "date", "time", "select");
    private static final Set<String> SECRET_NAMES = Set.of("password", "secret", "token", "authorization", "jwt",
            "apikey", "credential", "cookie", "sessionid", "salary", "bankaccount", "idcard");
    private ApprovalApplicationAgentFieldProjection() { }

    static List<Field> project(ObjectMapper mapper, String schema, String data) {
        try {
            JsonNode root = mapper.readTree(schema);
            JsonNode values = mapper.readTree(data);
            if (root == null || !root.path("fields").isArray() || root.path("fields").size() > 100
                    || values == null || !values.isObject()) throw invalid();
            List<Field> result = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (JsonNode definition : root.path("fields")) {
                String name = text(definition.path("name"), 120);
                String label = definition.has("label") ? text(definition.path("label"), 200) : name;
                String type = definition.has("type") ? text(definition.path("type"), 40) : "text";
                if (!names.add(name)) throw invalid();
                boolean hidden = !TYPES.contains(type) || definition.path("sensitive").asBoolean()
                        || SECRET_NAMES.contains(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
                List<String> projected = new ArrayList<>();
                JsonNode value = values.get(name);
                if (!hidden && value != null && !value.isNull()) {
                    if (value.isArray()) {
                        if (value.size() > 20) throw invalid();
                        for (JsonNode item : value) projected.add(scalar(item));
                    } else projected.add(scalar(value));
                }
                result.add(new Field(name, label, type, projected, hidden));
            }
            return List.copyOf(result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static String text(JsonNode value, int limit) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > limit) throw invalid();
        return value.asText();
    }
    private static String scalar(JsonNode value) {
        if (!value.isTextual() && !value.isNumber() && !value.isBoolean()) throw invalid();
        String text = value.asText();
        if (text.length() > 500) throw invalid();
        return text;
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BUSINESS_STATE_INVALID); }
}
