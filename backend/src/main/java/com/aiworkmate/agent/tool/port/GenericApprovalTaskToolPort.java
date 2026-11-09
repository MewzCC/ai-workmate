package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Assigned generic approval tasks; no raw form schema, process rules or attachment locations. */
public interface GenericApprovalTaskToolPort {
    Detail detailAssigned(ToolActorContext context, DetailQuery query);
    WriteResult approve(ToolActorContext context, DecisionCommand command);
    WriteResult reject(ToolActorContext context, DecisionCommand command);
    ApprovalTaskToolPort.Participants participants(ToolActorContext context, ParticipantQuery query);
    ParticipantResult transfer(ToolActorContext context, ApprovalTaskToolPort.ParticipantCommand command);
    ParticipantResult copyTo(ToolActorContext context, ApprovalTaskToolPort.ParticipantCommand command);
    record ParticipantQuery(long taskId,String purpose,String keyword,int page,int size) { }
    record ParticipantResult(long applicationId,long taskId,String status,int version,String applicationStatus,
            String action,long originalAssigneeUserId,long targetUserId,long assigneeUserId) implements ToolWriteReceipt { }

    record DecisionCommand(long taskId, int version, String comment) { }
    /** Status and version belong to the decided node; applicationStatus describes the whole request. */
    record WriteResult(long applicationId, long taskId, String status, int version, String applicationStatus, String action)
            implements ToolWriteReceipt { }

    record DetailQuery(long taskId, int fieldPage) { }

    record Detail(long taskId, long applicationId, String applicantName, String formName, String title,
                  String nodeName, String status, int version, String instanceStatus, String applicationStatus,
                  LocalDateTime submittedAt, LocalDateTime dueAt, boolean overdue,
                  List<ApprovalApplicationToolPort.DetailField> fields, int fieldsTotal, int fieldPage, int fieldPageSize,
                  boolean canApprove, boolean canReject, String blockedReason) {
        public Detail { fields = List.copyOf(fields); }
    }
}
