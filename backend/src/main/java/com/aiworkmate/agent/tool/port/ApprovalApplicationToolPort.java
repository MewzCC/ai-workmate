package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral boundary for self-owned generic approval application writes. */
public interface ApprovalApplicationToolPort {
    WriteResult createDraft(ToolActorContext context, Draft command, String operationKey);

    record Draft(String formKey, String processKey, List<FieldValue> fields) {
        public Draft {
            fields = List.copyOf(fields);
        }
    }

    record FieldValue(String name, List<String> values, boolean multiple) {
        public FieldValue {
            values = List.copyOf(values);
        }
    }

    record WriteResult(long applicationId, String formKey, String status, int version) { }
}
