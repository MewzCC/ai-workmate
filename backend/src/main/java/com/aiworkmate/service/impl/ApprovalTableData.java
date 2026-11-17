package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ApprovalTableColumn;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded data contract for one application field. Rows never represent executable business commands. */
final class ApprovalTableData {
    static final int MAX_ROWS = 20;
    static final int MAX_COLUMNS = 10;
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "money", "date", "time", "radio", "select");
    private static final Set<String> COLUMN_KEYS = Set.of("name", "label", "type", "required", "options", "min");
    private ApprovalTableData() { }

    static List<ApprovalTableColumn> columns(JsonNode definition) {
        String fieldName = definition.path("name").asText(definition.path("key").asText());
        if (Set.of("constructor", "prototype", "__proto__").contains(fieldName)) throw invalid();
        var columns = definition.path("columns");
        if (!columns.isArray() || columns.isEmpty() || columns.size() > MAX_COLUMNS) throw invalid();
        var result = new ArrayList<ApprovalTableColumn>();
        var names = new HashSet<String>();
        for (JsonNode column : columns) {
            if (!column.isObject()) throw invalid();
            column.fieldNames().forEachRemaining(key -> { if (!COLUMN_KEYS.contains(key)) throw invalid(); });
            String name = text(column.path("name"), 40), label = text(column.path("label"), 40);
            String type = text(column.path("type"), 40);
            if (!name.matches("[a-z][A-Za-z0-9_-]{0,39}") || !names.add(name) || !TYPES.contains(type)) throw invalid();
            if (column.has("required") && !column.path("required").isBoolean()) throw invalid();
            var options = new ArrayList<String>();
            if (column.has("options")) {
                if (!column.path("options").isArray() || column.path("options").size() > 10) throw invalid();
                for (JsonNode option : column.path("options")) options.add(text(option, 80));
                if (new HashSet<>(options).size() != options.size()) throw invalid();
            }
            if (Set.of("radio", "select").contains(type) && options.isEmpty()) throw invalid();
            BigDecimal min = null;
            if (column.has("min") && !column.path("min").isNull()) {
                if (!Set.of("number", "money").contains(type) || !column.path("min").isNumber()) throw invalid();
                min = column.path("min").decimalValue();
                if (min.abs().compareTo(new BigDecimal("1000000000000000")) > 0) throw invalid();
            }
            result.add(new ApprovalTableColumn(name, label, type, column.path("required").asBoolean(), options, min));
        }
        return List.copyOf(result);
    }

    static void validate(Object value, List<ApprovalTableColumn> columns, boolean requireAll) {
        if (!(value instanceof List<?> rows) || rows.size() > MAX_ROWS || columns.isEmpty() || columns.size() > MAX_COLUMNS) throw invalid();
        var byName = new LinkedHashMap<String, ApprovalTableColumn>();
        for (var column : columns) if (byName.put(column.name(), column) != null) throw invalid();
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> row) || row.size() > columns.size()) throw invalid();
            for (Object name : row.keySet()) if (!byName.containsKey(name)) throw invalid();
            for (var column : columns) {
                Object cell = row.get(column.name());
                if (cell == null || cell instanceof String text && text.isBlank()) {
                    if (requireAll && column.required()) throw invalid();
                } else validateCell(cell, column);
            }
        }
    }

    static List<Map<String, Object>> normalize(Object value, List<ApprovalTableColumn> columns, boolean requireAll) {
        validate(value, columns, requireAll);
        var result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) value) {
            Map<?, ?> row = (Map<?, ?>) item;
            var copy = new LinkedHashMap<String, Object>();
            for (var column : columns) {
                if (!row.containsKey(column.name())) continue;
                Object cell = row.get(column.name());
                if (cell instanceof Number number && Set.of("number", "money").contains(column.type())) {
                    cell = decimalText(number);
                }
                copy.put(column.name(), cell);
            }
            result.add(java.util.Collections.unmodifiableMap(copy));
        }
        return List.copyOf(result);
    }

    private static void validateCell(Object value, ApprovalTableColumn column) {
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) throw invalid();
        String text = value instanceof Number number ? decimalText(number) : value.toString();
        if (text.length() > 500) throw invalid();
        switch (column.type()) {
            case "number", "money" -> {
                if (!(value instanceof Number) && !text.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid();
                try {
                    var amount = new BigDecimal(text);
                    if (!Double.isFinite(amount.doubleValue()) || column.min() != null && amount.compareTo(column.min()) < 0) throw invalid();
                } catch (NumberFormatException invalidNumber) { throw invalid(); }
            }
            case "date" -> {
                if (!(value instanceof String) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
                try { LocalDate.parse(text); }
                catch (DateTimeParseException invalidDate) { throw invalid(); }
            }
            case "time" -> {
                if (!(value instanceof String) || !text.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw invalid();
            }
            case "radio", "select" -> {
                if (!(value instanceof String) || !column.options().isEmpty() && !column.options().contains(text)) throw invalid();
            }
            case "text", "textarea" -> { if (!(value instanceof String)) throw invalid(); }
            default -> throw invalid();
        }
    }

    private static String decimalText(Number number) {
        try {
            var decimal = new BigDecimal(number.toString());
            long integerDigits = Math.max(1L, (long) decimal.precision() - decimal.scale());
            long fractionalDigits = Math.max(0L, decimal.scale());
            long length = integerDigits + fractionalDigits + (fractionalDigits > 0 ? 1 : 0) + (decimal.signum() < 0 ? 1 : 0);
            if (length > 500) throw invalid();
            return decimal.toPlainString();
        }
        catch (NumberFormatException exception) { throw invalid(); }
    }

    private static String text(JsonNode value, int max) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) throw invalid();
        return value.asText();
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID, "validation.approval.submit.valueInvalid");
    }
}
