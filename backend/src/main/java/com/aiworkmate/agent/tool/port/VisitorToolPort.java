package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitorToolPort {
    Page query(ToolActorContext context, Query query);
    ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);
    VisitResult checkIn(ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<VisitResult> findCheckIn(
            ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    VisitResult markArrived(ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<VisitResult> findArrival(
            ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    VisitResult leave(ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<VisitResult> findLeave(
            ToolActorContext context, VisitCommand command, ToolOperationKey operationKey);
    enum Queue { MINE, PENDING }
    record Query(Long bookingId, Queue queue, String status, int page, int size) { }
    record ApplicationCommand(String visitorName, String visitorCompany, String visitorPhone,
                              String purpose, long hostUserId, LocalDateTime expectedVisitAt,
                              LocalDateTime expectedLeaveAt, String plateNumber, int partySize) { }
    record ApplicationResult(long bookingId, String status, int version, LocalDateTime submittedAt)
            implements ToolWriteReceipt { }
    record VisitCommand(long bookingId, int version, String remark) { }
    record VisitResult(long bookingId, String status, int version, LocalDateTime occurredAt)
            implements ToolWriteReceipt { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String applicantName, String approverName, String hostName,
                String visitorName, String visitorCompany, String purpose,
                LocalDateTime expectedVisitAt, LocalDateTime expectedLeaveAt, Integer partySize,
                String status, int version, String taskStatus, LocalDateTime submittedAt,
                LocalDateTime completedAt, String registeredByName, LocalDateTime checkedInAt,
                LocalDateTime visitedAt, LocalDateTime leftAt, LocalDateTime noShowAt,
                boolean canWithdraw, boolean canDecide, boolean canCheckIn,
                boolean canMarkVisited, boolean canLeave, boolean canMarkNoShow) { }
}
