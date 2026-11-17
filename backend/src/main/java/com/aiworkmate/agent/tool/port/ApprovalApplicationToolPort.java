package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral boundary for self-owned generic approval applications. */
public interface ApprovalApplicationToolPort {
    ToolPage<Summary> mine(ToolActorContext context, Query query);

    Detail detailMine(ToolActorContext context, DetailQuery query);

    record DetailQuery(long applicationId, int fieldPage) { }

    record Detail(Summary application, String schemaSource, Integer formVersion, List<DetailField> fields,
                  int fieldsTotal, int fieldPage) {
        public Detail { fields = List.copyOf(fields); }
    }
    record DetailTableColumn(String name, String label, String type) { }
    record DetailTable(List<DetailTableColumn> columns, List<List<String>> rows) {
        public DetailTable {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }
    record DetailField(String name, String label, String type, List<String> values, boolean redacted,
            DetailTable table) {
        public DetailField { values = List.copyOf(values); }
        public DetailField(String name, String label, String type, List<String> values, boolean redacted) {
            this(name, label, type, values, redacted, null);
        }
    }

    record Query(String status, String formKey, int page, int size) { }

    /** No raw form data, schema, employee identities or workflow internals. */
    record Summary(long applicationId, String formKey, String formName, String status, int version,
                   String submittedAt, String completedAt, String createdAt, boolean overdue,
                   int reminderCount, boolean canWithdraw, boolean canEditDraft, boolean canCancel,
                   boolean canRemind) { }

    WriteResult createDraft(ToolActorContext context, Draft command, ToolOperationKey operationKey);
    WriteResult apply(ToolActorContext context, Draft command, ToolOperationKey operationKey);

    WriteResult updateDraft(ToolActorContext context, long applicationId, int version, DraftUpdate command);

    WriteResult submitDraft(ToolActorContext context, long applicationId, int version);

    WriteResult cancelDraft(ToolActorContext context, long applicationId, int version);

    WriteResult withdraw(ToolActorContext context, long applicationId, int version);

    WriteResult reopen(ToolActorContext context, long applicationId, int version);

    WriteResult remind(ToolActorContext context, long applicationId, int version);

    record Draft(String formKey, String processKey, List<FieldValue> fields) {
        public Draft {
            fields = List.copyOf(fields);
        }
    }

    record DraftUpdate(String processKey, List<FieldValue> fields) {
        public DraftUpdate { fields = List.copyOf(fields); }
    }

    record TableCell(String name, String value) { }

    record TableRow(List<TableCell> cells) {
        public TableRow { cells = List.copyOf(cells); }
    }

    record FieldValue(String name, List<String> values, boolean multiple, List<TableRow> rows) {
        public FieldValue {
            values = List.copyOf(values);
            rows = rows == null ? null : List.copyOf(rows);
        }
        public FieldValue(String name, List<String> values, boolean multiple) {
            this(name, values, multiple, null);
        }
    }

    record WriteResult(long applicationId, String formKey, String status, int version)
            implements ToolWriteReceipt { }
}
