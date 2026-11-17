package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

final class ApprovalApplicationDraftArguments {
    private ApprovalApplicationDraftArguments() { }

    static List<ApprovalApplicationToolPort.FieldValue> parseFields(JsonNode arguments) {
        JsonNode fieldsNode = arguments.path("fields");
        if (!fieldsNode.isArray() || fieldsNode.size() > 100) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        List<ApprovalApplicationToolPort.FieldValue> fields = new ArrayList<>(fieldsNode.size());
        for (JsonNode field : fieldsNode) {
            String name = requiredText(field, "name");
            JsonNode single = field.get("value");
            JsonNode multiple = field.get("values");
            JsonNode rows = field.get("rows");
            if ((single == null ? 0 : 1) + (multiple == null ? 0 : 1) + (rows == null ? 0 : 1) != 1) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            if (rows != null) {
                fields.add(new ApprovalApplicationToolPort.FieldValue(name, List.of(), false, parseRows(rows)));
                continue;
            }
            if (single != null) {
                if (!single.isTextual()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
                fields.add(new ApprovalApplicationToolPort.FieldValue(name, List.of(single.asText()), false));
                continue;
            }
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
        return List.copyOf(fields);
    }

    private static List<ApprovalApplicationToolPort.TableRow> parseRows(JsonNode rows) {
        if (!rows.isArray() || rows.size() > 20) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        List<ApprovalApplicationToolPort.TableRow> result = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode cells = row.path("cells");
            if (!row.isObject() || row.size() != 1 || !cells.isArray() || cells.size() > 10) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            List<ApprovalApplicationToolPort.TableCell> parsed = new ArrayList<>();
            var names = new java.util.HashSet<String>();
            for (JsonNode cell : cells) {
                String name = requiredText(cell, "name");
                JsonNode value = cell.path("value");
                if (!cell.isObject() || cell.size() != 2 || !name.matches("[a-z][A-Za-z0-9_-]{0,39}")
                        || !names.add(name) || !value.isTextual() || value.textValue().length() > 500) {
                    throw new BusinessException(ErrorCode.REQUEST_INVALID);
                }
                parsed.add(new ApprovalApplicationToolPort.TableCell(name, value.textValue()));
            }
            result.add(new ApprovalApplicationToolPort.TableRow(parsed));
        }
        return List.copyOf(result);
    }
}
