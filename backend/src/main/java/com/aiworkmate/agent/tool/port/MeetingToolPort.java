package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

public interface MeetingToolPort {
    Result query(ToolActorContext context, Query query);
    WriteResult book(ToolActorContext context, BookCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<WriteResult> findCreation(
            ToolActorContext context, BookCommand expectedCommand, ToolOperationKey operationKey);
    CancelResult cancel(ToolActorContext context, CancelCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<CancelResult> findCancellation(
            ToolActorContext context, CancelCommand expectedCommand, ToolOperationKey operationKey);
    record Query(String keyword, String roomStatus, LocalDateTime from, LocalDateTime to,
                 String bookingStatus, int page, int size) {}
    record BookCommand(long roomId, String title, String agenda, LocalDateTime startAt,
                       LocalDateTime endAt, int attendeeCount) {}
    record CancelCommand(long bookingId, int version, String reason) {}
    record WriteResult(long bookingId, long roomId, String status, int version,
                       LocalDateTime startAt, LocalDateTime endAt) implements ToolWriteReceipt {}
    record CancelResult(long bookingId, long roomId, String status, int version,
                        LocalDateTime cancelledAt) implements ToolWriteReceipt {}
    record Result(List<Room> rooms, List<Booking> bookings, long bookingTotal, int page, int size) {
        public Result { rooms = List.copyOf(rooms); bookings = List.copyOf(bookings); }
    }
    record Room(long id, String code, String name, String location, int capacity, String facilities,
                String status, String remark, boolean canEdit, boolean canDelete) {}
    record Booking(long id, long roomId, String roomCode, String roomName, String roomLocation,
                   String organizerName, String title, String agenda, LocalDateTime startAt,
                   LocalDateTime endAt, int attendeeCount, String status, int version,
                   String cancelledByName, LocalDateTime cancelledAt, String cancelReason,
                   LocalDateTime createdAt, LocalDateTime updatedAt, boolean canCancel) {}
}
