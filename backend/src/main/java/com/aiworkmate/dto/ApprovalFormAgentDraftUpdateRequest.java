package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded update command used by the controlled Agent adapter. */
public record ApprovalFormAgentDraftUpdateRequest(
        Integer version,
        String formName,
        String description,
        List<Field> fields
) {
    public ApprovalFormAgentDraftUpdateRequest {
        fields = List.copyOf(fields);
    }

    public record Field(String name, String label, String type, boolean required,
                        String placeholder, List<String> options, String width) {
        public Field { options = List.copyOf(options); }
    }
}
