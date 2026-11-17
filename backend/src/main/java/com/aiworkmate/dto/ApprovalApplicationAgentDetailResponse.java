package com.aiworkmate.dto;

import java.util.List;

/** Bounded self-owned field projection; no raw schema, workflow rules or identities. */
public record ApprovalApplicationAgentDetailResponse(
        Application application, String schemaSource, Integer formVersion,
        List<Field> fields, int fieldsTotal, int fieldPage) {
    public ApprovalApplicationAgentDetailResponse { fields = List.copyOf(fields); }
    public record Application(long applicationId, String formKey, String formName, String status, int version,
            java.time.LocalDateTime submittedAt, java.time.LocalDateTime completedAt, java.time.LocalDateTime createdAt,
            boolean overdue, int reminderCount, boolean canWithdraw, boolean canEditDraft, boolean canCancel, boolean canRemind) {
        public static Application from(ApprovalApplicationResponse value) {
            return new Application(value.id(), value.formKey(), value.formName(), value.status(), value.version(),
                    value.submittedAt(), value.completedAt(), value.createdAt(), value.overdue(), value.reminderCount(),
                    value.canWithdraw(), value.canEditDraft(), value.canCancel(), value.canRemind());
        }
    }
    public record TableColumn(String name, String label, String type) { }
    public record Table(List<TableColumn> columns, List<List<String>> rows) {
        public Table {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }
    public record Field(String name, String label, String type, List<String> values, boolean redacted,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Table table) {
        public Field { values = List.copyOf(values); }
        public Field(String name, String label, String type, List<String> values, boolean redacted) {
            this(name, label, type, values, redacted, null);
        }
    }
}
