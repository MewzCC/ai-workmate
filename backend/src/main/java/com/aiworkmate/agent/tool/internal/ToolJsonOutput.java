package com.aiworkmate.agent.tool.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

final class ToolJsonOutput {
    private ToolJsonOutput() {}

    static JsonNode omitNulls(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> nullFields = new ArrayList<>();
            object.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNull()) nullFields.add(entry.getKey());
                else omitNulls(entry.getValue());
            });
            nullFields.forEach(object::remove);
        } else if (node instanceof ArrayNode array) {
            array.forEach(ToolJsonOutput::omitNulls);
        }
        return node;
    }
}
