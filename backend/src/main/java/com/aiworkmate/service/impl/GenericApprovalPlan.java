package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Bounded, data-only execution plan. Unknown flow semantics never become an implicit approval. */
public record GenericApprovalPlan(int version, List<GenericApprovalPlan.Step> steps) {
    public record Step(int index, String name, long assigneeUserId, int dueHours) { }
    private static final Set<String> NODE_FIELDS = Set.of("nodeType", "nodeName", "approveType", "targetKey", "mode", "timeoutEnabled", "timeoutHours", "timeoutAction");
    public GenericApprovalPlan { steps = List.copyOf(steps); }

    static GenericApprovalPlan compile(ObjectMapper mapper, String json, long defaultHours, Function<JsonNode, List<Long>> resolver) {
        JsonNode nodes = read(mapper, json);
        if (!nodes.isArray() || nodes.isEmpty() || nodes.size() > 20) throw unsupported();
        boolean framed = nodes.get(0).path("nodeType").asText().equals("START");
        if (framed && !nodes.get(nodes.size() - 1).path("nodeType").asText().equals("END")) throw unsupported();
        var steps = new ArrayList<Step>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            if (!node.isObject()) throw unsupported();
            node.fieldNames().forEachRemaining(field -> { if (!NODE_FIELDS.contains(field)) throw unsupported(); });
            for (String text : List.of("nodeName", "nodeType", "approveType", "targetKey", "mode", "timeoutAction"))
                if (node.has(text) && !node.path(text).isTextual()) throw unsupported();
            if (node.has("timeoutEnabled") && !node.path("timeoutEnabled").isBoolean()) throw unsupported();
            String type = node.path("nodeType").asText("APPROVAL");
            if (framed && i == 0 && type.equals("START") || framed && i == nodes.size() - 1 && type.equals("END")) continue;
            if (!type.equals("APPROVAL")) throw unsupported();
            String name = node.path("nodeName").asText("");
            if (name.isBlank() || name.length() > 80) throw unsupported();
            int hours = (int) Math.max(0, Math.min(720, defaultHours));
            if (node.path("timeoutEnabled").asBoolean(false)) {
                if (!node.path("timeoutHours").isInt() || node.path("timeoutHours").asInt() < 1 || node.path("timeoutHours").asInt() > 720
                        || !node.path("timeoutAction").asText("REMIND").equals("REMIND")) throw unsupported();
                hours = node.path("timeoutHours").asInt();
            }
            var users = resolver.apply(node);
            if (users == null || users.isEmpty() || users.size() > 50 || users.stream().anyMatch(id -> id == null || id < 1) || Set.copyOf(users).size() != users.size()) throw unsupported();
            String mode = node.path("mode").asText("SEQUENTIAL");
            if (!Set.of("SEQUENTIAL", "COUNTERSIGN", "OR_SIGN").contains(mode) || !mode.equals("SEQUENTIAL") && users.size() != 1) throw unsupported();
            for (Long user : users) {
                if (user == null || user < 1 || steps.size() >= 50) throw unsupported();
                steps.add(new Step(steps.size(), name, user, hours));
            }
        }
        if (steps.isEmpty()) throw unsupported();
        return new GenericApprovalPlan(1, steps);
    }

    static GenericApprovalPlan decode(ObjectMapper mapper, String json) {
        JsonNode root = read(mapper, json);
        if (!root.isObject() || root.size() != 2 || !root.path("version").isInt() || root.path("version").asInt() != 1
                || !root.path("steps").isArray() || root.path("steps").isEmpty() || root.path("steps").size() > 50) throw unsupported();
        var steps = new ArrayList<Step>();
        for (JsonNode item : root.path("steps")) {
            if (!item.isObject() || item.size() != 4 || !item.path("index").isInt() || item.path("index").asInt() != steps.size()
                    || !item.path("name").isTextual() || item.path("name").asText().isBlank() || item.path("name").asText().length() > 80
                    || !item.path("assigneeUserId").isIntegralNumber() || !item.path("assigneeUserId").canConvertToLong() || item.path("assigneeUserId").asLong() < 1
                    || !item.path("dueHours").isInt() || item.path("dueHours").asInt() < 0 || item.path("dueHours").asInt() > 720) throw unsupported();
            steps.add(new Step(item.path("index").asInt(), item.path("name").asText(), item.path("assigneeUserId").asLong(), item.path("dueHours").asInt()));
        }
        return new GenericApprovalPlan(1, steps);
    }

    /** Matching rule actions need the next rule-execution slice; never approve while silently skipping them. */
    static void requireRulesHandled(ObjectMapper mapper, String rulesJson, String dataJson) {
        JsonNode rules = read(mapper, rulesJson), data = read(mapper, dataJson);
        if (!rules.isArray() || rules.size() > 100 || !data.isObject()) throw unsupported();
        for (JsonNode rule : rules) {
            JsonNode action = read(mapper, rule.path("actionJson").asText());
            if (!action.isObject() || !action.path("enabled").isBoolean()) throw unsupported();
            if (action.path("enabled").asBoolean() && matches(read(mapper, rule.path("conditionJson").asText()), data, 0)) throw unsupported();
        }
    }
    private static boolean matches(JsonNode condition, JsonNode data, int depth) {
        if (!condition.isObject() || depth > 3) throw unsupported();
        if (condition.has("conditions")) {
            JsonNode children = condition.path("conditions"); String logic = condition.path("logic").asText();
            if (!children.isArray() || children.isEmpty() || children.size() > 20 || !Set.of("AND", "OR").contains(logic)) throw unsupported();
            var values = new ArrayList<Boolean>(); children.forEach(child -> values.add(matches(child, data, depth + 1)));
            return logic.equals("AND") ? values.stream().allMatch(Boolean::booleanValue) : values.stream().anyMatch(Boolean::booleanValue);
        }
        String field = condition.path("field").asText(), op = condition.path("op").asText();
        if (!field.matches("^[a-z][A-Za-z0-9_]{0,39}$") || !Set.of("eq", "ne", "neq", "gt", "gte", "lt", "lte", "in", "contains").contains(op) || !condition.has("value")) throw unsupported();
        JsonNode actual = data.get(field), expected = condition.path("value");
        if (actual == null || actual.isNull()) return false;
        if (op.equals("in")) { if (!expected.isArray() || expected.size() > 20) throw unsupported(); for (JsonNode item : expected) if (equal(actual, item)) return true; return false; }
        if (op.equals("contains")) { if (!actual.isTextual() || !expected.isTextual()) throw unsupported(); return actual.asText().contains(expected.asText()); }
        if (Set.of("eq", "ne", "neq").contains(op)) return op.equals("eq") == equal(actual, expected);
        try {
            int compared = new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText()));
            return switch (op) { case "gt" -> compared > 0; case "gte" -> compared >= 0; case "lt" -> compared < 0; default -> compared <= 0; };
        } catch (NumberFormatException invalid) { throw unsupported(); }
    }
    private static boolean equal(JsonNode a, JsonNode b) {
        if (a.isBoolean() && b.isTextual() || b.isBoolean() && a.isTextual()) {
            String text = a.isTextual() ? a.asText() : b.asText();
            if (!Set.of("true", "false").contains(text.toLowerCase(java.util.Locale.ROOT))) throw unsupported();
            return (a.isBoolean() ? a.asBoolean() : b.asBoolean()) == Boolean.parseBoolean(text);
        }
        if ((a.isNumber() || a.isTextual()) && (b.isNumber() || b.isTextual()) && a.asText().matches("^-?\\d+(\\.\\d+)?$") && b.asText().matches("^-?\\d+(\\.\\d+)?$")) {
            if (a.asText().length() > 500 || b.asText().length() > 500) throw unsupported();
            return new BigDecimal(a.asText()).compareTo(new BigDecimal(b.asText())) == 0;
        }
        return a.equals(b);
    }
    private static JsonNode read(ObjectMapper mapper, String value) {
        if (value == null || value.length() > 262144) throw unsupported();
        try {
            JsonNode result = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(value);
            if (result == null) throw unsupported(); return result;
        } catch (Exception invalid) { throw unsupported(); }
    }
    static BusinessException unsupported() { return new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.runtimeUnavailable"); }
}
