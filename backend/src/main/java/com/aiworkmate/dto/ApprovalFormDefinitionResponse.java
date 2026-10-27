package com.aiworkmate.dto;

import java.util.List;

/** Bounded semantic definition, never raw schema JSON or application data. */
public record ApprovalFormDefinitionResponse(long formId, String formKey, String formName,
                                             String status, int version, List<Field> fields) {
    public ApprovalFormDefinitionResponse { fields = List.copyOf(fields); }
    public record Field(String name, String label, String type, boolean required,
                        String placeholder, List<String> options, String width, java.math.BigDecimal min) {
        public Field { options = List.copyOf(options); }
    }
}
