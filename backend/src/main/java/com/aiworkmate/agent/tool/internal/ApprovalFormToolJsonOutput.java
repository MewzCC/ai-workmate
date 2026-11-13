package com.aiworkmate.agent.tool.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Optional column definitions belong to the tool wire format, not the transport-neutral port. */
final class ApprovalFormToolJsonOutput {
    private ApprovalFormToolJsonOutput() { }

    static void omitEmptyColumns(JsonNode fields) {
        for (JsonNode field : fields) {
            if (field instanceof ObjectNode object && field.path("columns").isArray() && field.path("columns").isEmpty()) {
                object.remove("columns");
            }
        }
    }
}
