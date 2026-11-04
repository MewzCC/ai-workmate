package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Assigned generic approval tasks; no raw form schema, process rules or attachment locations. */
public interface GenericApprovalTaskToolPort {
    Detail detailAssigned(ToolActorContext context, DetailQuery query);

    record DetailQuery(long taskId, int fieldPage) { }

    record Detail(long taskId, long applicationId, String applicantName, String formName, String title,
                  String nodeName, String status, int version, String instanceStatus, String applicationStatus,
                  LocalDateTime submittedAt, LocalDateTime dueAt, boolean overdue,
                  List<ApprovalApplicationToolPort.DetailField> fields, int fieldsTotal, int fieldPage, int fieldPageSize,
                  boolean canApprove, boolean canReject, String blockedReason) {
        public Detail { fields = List.copyOf(fields); }
    }
}
