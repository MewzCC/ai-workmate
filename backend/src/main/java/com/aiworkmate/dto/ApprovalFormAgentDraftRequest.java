package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded command used by the controlled Agent adapter. */
public record ApprovalFormAgentDraftRequest(
        String formKey,
        String formName,
        String description,
        List<Field> fields
) {
    public ApprovalFormAgentDraftRequest {
        fields = List.copyOf(fields);
    }

    public record Field(String name, String label, String type, boolean required,
                        String placeholder, List<String> options, String width) {
        public Field { options = List.copyOf(options); }
    }
}
