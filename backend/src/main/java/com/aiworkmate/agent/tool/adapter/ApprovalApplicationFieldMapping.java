package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort.DetailField;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort.DetailTable;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort.DetailTableColumn;
import com.aiworkmate.dto.ApprovalApplicationAgentDetailResponse.Field;

/** Keeps self-owned and assigned-task historical field projections identical. */
final class ApprovalApplicationFieldMapping {
    private ApprovalApplicationFieldMapping() { }

    static DetailField map(Field field) {
        var table = field.table();
        return new DetailField(field.name(), field.label(), field.type(), field.values(), field.redacted(),
                table == null ? null : new DetailTable(table.columns().stream()
                        .map(column -> new DetailTableColumn(column.name(), column.label(), column.type())).toList(), table.rows()));
    }
}
